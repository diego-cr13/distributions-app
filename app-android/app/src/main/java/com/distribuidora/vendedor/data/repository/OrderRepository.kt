package com.distribuidora.vendedor.data.repository

import com.distribuidora.vendedor.core.OrderStatus
import com.distribuidora.vendedor.core.nowUtc
import com.distribuidora.vendedor.core.subtotal
import com.distribuidora.vendedor.core.toMoney
import com.distribuidora.vendedor.core.toMoneyString
import com.distribuidora.vendedor.core.total
import com.distribuidora.vendedor.data.local.AppDatabase
import com.distribuidora.vendedor.data.local.OrderEntity
import com.distribuidora.vendedor.data.local.OrderItemEntity
import com.distribuidora.vendedor.data.local.OrderItemRow
import com.distribuidora.vendedor.data.local.OrderSummary
import com.distribuidora.vendedor.data.local.ProductEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/** A line of the order being built: product from the local catalog and an integer quantity. */
data class NewOrderLine(val product: ProductEntity, val quantity: Int)

/** RF-3 / RF-5: local orders. Creation never touches the local stock; the server does. */
class OrderRepository(private val db: AppDatabase) {
    private val orderDao get() = db.orderDao()

    fun observeSummaries(): Flow<List<OrderSummary>> = orderDao.observeSummaries()

    fun observeSummary(id: String): Flow<OrderSummary?> = orderDao.observeSummary(id)

    fun observeItems(id: String): Flow<List<OrderItemRow>> = orderDao.observeItemRows(id)

    fun observePendingCount(): Flow<Int> = orderDao.observePendingCount()

    suspend fun pendingCount(): Int = orderDao.pendingCount()

    /**
     * Saves a `pendiente` order with a phone-generated uuid (the idempotency key of
     * `enviar_pedido`). Preconditions the RPC demands are enforced here: a client
     * from the local copy, at least one line, integer quantity > 0 and each product
     * once. Subtotals and total use BigDecimal HALF_UP. Returns the new order id.
     */
    suspend fun createPendingOrder(clientId: String, lines: List<NewOrderLine>): String {
        require(clientId.isNotBlank()) { "client required" }
        val valid = lines.filter { it.quantity > 0 }
        require(valid.isNotEmpty()) { "at least one item required" }
        require(valid.map { it.product.id }.toSet().size == valid.size) { "duplicate product" }
        requireNotNull(db.clientDao().getById(clientId)) { "client not in local copy" }

        val id = UUID.randomUUID().toString()
        val items = valid.map { line ->
            val price = line.product.priceBs.toMoney()
            OrderItemEntity(
                orderId = id,
                productId = line.product.id,
                quantity = line.quantity,
                priceSeenBs = price.toMoneyString(),
                priceAppliedBs = null,
                subtotalBs = subtotal(line.quantity, price).toMoneyString(),
                problem = null,
            )
        }
        val order = OrderEntity(
            id = id,
            clientId = clientId,
            status = OrderStatus.PENDING,
            reason = null,
            totalBs = total(items.map { it.subtotalBs.toMoney() }).toMoneyString(),
            createdOnDevice = nowUtc(),
            receivedAt = null,
            supervisorNote = null,
            attempts = 0,
            lastError = null,
            updatedAt = null,
        )
        orderDao.insertOrderWithItems(order, items)
        return id
    }

    /** Logout: wipes local orders, items and the orders sync marker. */
    suspend fun clearLocalOrders() {
        orderDao.clearAll()
        db.syncMetaDao().delete(SyncTables.ORDERS)
    }
}
