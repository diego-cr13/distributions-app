package com.distribuidora.vendedor.data.repository

import com.distribuidora.vendedor.core.OrderStatus
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.core.toMoneyString
import com.distribuidora.vendedor.core.toUtc
import com.distribuidora.vendedor.data.local.AppDatabase
import com.distribuidora.vendedor.data.local.ClientEntity
import com.distribuidora.vendedor.data.local.OrderEntity
import com.distribuidora.vendedor.data.local.OrderItemEntity
import com.distribuidora.vendedor.data.local.ProductEntity
import com.distribuidora.vendedor.data.local.SyncMetaEntity
import com.distribuidora.vendedor.data.remote.ClientDto
import com.distribuidora.vendedor.data.remote.OrderDto
import com.distribuidora.vendedor.data.remote.ProductDto
import com.distribuidora.vendedor.data.remote.SendOrderItem
import com.distribuidora.vendedor.data.remote.SendOrderRequest
import com.distribuidora.vendedor.data.remote.SendOrderResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of the last sync, for the UI ("Sincronizado" / error notice). */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Running : SyncStatus()
    data object Success : SyncStatus()
    data class Failure(val error: SyncError) : SyncStatus()
}

/**
 * RF-2 / RF-4: upload of pending orders through `enviar_pedido`, then download of
 * catalog, clients and own orders by delta over `actualizado_en`. The server always wins.
 */
class SyncRepository(
    private val supabase: SupabaseClient,
    private val db: AppDatabase,
    private val auth: AuthRepository,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status

    /** One sync at a time: the worker and "Sincronizar ahora" may overlap. */
    private val syncMutex = Mutex()

    /**
     * Full sync: own profile check → upload pending → download catalog → download
     * orders. Records the outcome in [status] and rethrows it as a [SyncError].
     */
    suspend fun syncAll() {
        syncMutex.withLock {
            _status.value = SyncStatus.Running
            try {
                checkProfile()
                uploadPending()
                downloadCatalog()
                downloadOwnOrders()
                _status.value = SyncStatus.Success
            } catch (e: Exception) {
                val error = SyncError.from(e)
                _status.value = SyncStatus.Failure(error)
                throw error
            }
        }
    }

    fun clearStatus() {
        _status.value = SyncStatus.Idle
    }

    /**
     * RLS returns no row (not an error) for an inactive vendor, so reading the own
     * profile first is the only way to detect a deactivated account.
     */
    suspend fun checkProfile() {
        val profile = try {
            auth.fetchOwnProfile()
        } catch (e: Exception) {
            throw SyncError.from(e)
        }
        if (profile == null || !profile.isVendor) {
            throw SyncError.Auth("inactive or non-vendor profile")
        }
    }

    /**
     * Sends every `pendiente` order, oldest first, with its own uuid (idempotent on
     * the server). A response updates status, reason, total and per-item problems.
     * A [SyncError.Data] marks the order (`attempts + 1`, `lastError`) and continues
     * with the next one; network and auth errors stop the upload.
     */
    suspend fun uploadPending() {
        val orderDao = db.orderDao()
        for (order in orderDao.getPending()) {
            val items = orderDao.getItems(order.id)
            val request = SendOrderRequest(
                p_id = order.id,
                p_cliente_id = order.clientId,
                p_creado_en = order.createdOnDevice,
                p_items = items.map {
                    SendOrderItem(
                        producto_id = it.productId,
                        cantidad = it.quantity,
                        precio_visto_bs = it.priceSeenBs.toMoney(),
                    )
                },
            )
            val response = try {
                supabase.postgrest.rpc("enviar_pedido", request).decodeAs<SendOrderResponse>()
            } catch (e: Exception) {
                when (val error = SyncError.from(e)) {
                    is SyncError.Data -> {
                        orderDao.markSendFailed(order.id, error.message ?: "error")
                        continue
                    }
                    else -> throw error
                }
            }
            applySendResponse(order, items, response)
        }
    }

    private suspend fun applySendResponse(
        order: OrderEntity,
        items: List<OrderItemEntity>,
        response: SendOrderResponse,
    ) {
        val problems = response.items.associate { it.producto_id to it.problema }
        val updatedItems = items.map { it.copy(problem = problems[it.productId]) }
        db.orderDao().replaceFromServer(
            order.copy(
                status = response.estado.ifBlank { OrderStatus.SENT },
                reason = response.motivo,
                totalBs = response.total_bs.toMoneyString(),
                lastError = null,
            ),
            updatedItems,
        )
    }

    /** Products and clients changed since the last sync, upserted into Room. */
    suspend fun downloadCatalog() {
        val productsSince = db.syncMetaDao().lastSync(SyncTables.PRODUCTS)
        val products = supabase.from("productos")
            .select { delta(productsSince) }
            .decodeList<ProductDto>()
        if (products.isNotEmpty()) {
            db.productDao().upsertAll(products.map { it.toEntity() })
            db.syncMetaDao().upsert(SyncMetaEntity(SyncTables.PRODUCTS, products.maxUpdatedAt { it.actualizado_en }))
        }

        val clientsSince = db.syncMetaDao().lastSync(SyncTables.CLIENTS)
        val clients = supabase.from("clientes")
            .select { delta(clientsSince) }
            .decodeList<ClientDto>()
        if (clients.isNotEmpty()) {
            db.clientDao().upsertAll(clients.map { it.toEntity() })
            db.syncMetaDao().upsert(SyncMetaEntity(SyncTables.CLIENTS, clients.maxUpdatedAt { it.actualizado_en }))
        }
    }

    /**
     * Own orders (RLS limits them to the vendor) with their items, changed since the
     * last sync. Each one replaces the local header, even a `pendiente`, and swaps its items.
     */
    suspend fun downloadOwnOrders() {
        val since = db.syncMetaDao().lastSync(SyncTables.ORDERS)
        val orders = supabase.from("pedidos")
            .select(Columns.raw("*, pedido_items(*)")) { delta(since) }
            .decodeList<OrderDto>()
        if (orders.isEmpty()) return

        val orderDao = db.orderDao()
        for (dto in orders) {
            val local = orderDao.getById(dto.id)
            orderDao.replaceFromServer(dto.toEntity(attempts = local?.attempts ?: 0), dto.toItemEntities())
        }
        db.syncMetaDao().upsert(SyncMetaEntity(SyncTables.ORDERS, orders.maxUpdatedAt { it.actualizado_en }))
    }

    /** `actualizado_en >= lastSync` (a repeated row is upserted harmlessly), oldest first. */
    private fun PostgrestRequestBuilder.delta(since: String?) {
        if (since != null) {
            filter { gte("actualizado_en", since) }
        }
        order("actualizado_en", Order.ASCENDING)
    }

    private inline fun <T> List<T>.maxUpdatedAt(selector: (T) -> String): String =
        maxOf { toUtc(selector(it)) }

    private fun ProductDto.toEntity() = ProductEntity(
        id = id,
        code = codigo,
        name = nombre,
        unit = unidad,
        priceBs = precio_bs.toMoneyString(),
        stock = stock,
        updatedAt = toUtc(actualizado_en),
    )

    private fun ClientDto.toEntity() = ClientEntity(
        id = id,
        code = codigo,
        name = nombre,
        zone = zona.orEmpty(),
        city = ciudad.orEmpty(),
        updatedAt = toUtc(actualizado_en),
    )

    private fun OrderDto.toEntity(attempts: Int) = OrderEntity(
        id = id,
        clientId = cliente_id,
        status = estado,
        reason = motivo,
        totalBs = total_bs.toMoneyString(),
        createdOnDevice = toUtc(creado_en_dispositivo),
        receivedAt = toUtc(recibido_en),
        supervisorNote = nota_supervisor,
        attempts = attempts,
        lastError = null,
        updatedAt = toUtc(actualizado_en),
    )

    private fun OrderDto.toItemEntities(): List<OrderItemEntity> = pedido_items.map {
        OrderItemEntity(
            orderId = id,
            productId = it.producto_id,
            quantity = it.cantidad,
            priceSeenBs = it.precio_visto_bs.toMoneyString(),
            priceAppliedBs = it.precio_aplicado_bs?.toMoneyString(),
            subtotalBs = it.subtotal_bs.toMoneyString(),
            problem = it.problema,
        )
    }
}
