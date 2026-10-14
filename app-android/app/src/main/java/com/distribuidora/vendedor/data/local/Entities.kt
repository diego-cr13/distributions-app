package com.distribuidora.vendedor.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Local copy of `productos`. Money is text ("16.50"); timestamps are UTC ISO-8601
 * in `Instant.toString()` form (see core/Time.kt).
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val unit: String,
    val priceBs: String,
    val stock: Int,
    val updatedAt: String,
)

/** Local copy of `clientes`. */
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val zone: String,
    val city: String,
    val updatedAt: String,
)

/**
 * Local order. `id` is generated on the phone and is the idempotency key of
 * `enviar_pedido`. `status` is `pendiente` (local only) or one of the server
 * values `enviado | confirmado | con_problema | rechazado`.
 */
@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    val clientId: String,
    val status: String,
    val reason: String?,
    val totalBs: String,
    val createdOnDevice: String,
    val receivedAt: String?,
    val supervisorNote: String?,
    val attempts: Int,
    val lastError: String?,
    val updatedAt: String?,
)

/** Line of a local order. One row per product (PK `orderId, productId`). */
@Entity(tableName = "order_items", primaryKeys = ["orderId", "productId"])
data class OrderItemEntity(
    val orderId: String,
    val productId: String,
    val quantity: Int,
    val priceSeenBs: String,
    val priceAppliedBs: String?,
    val subtotalBs: String,
    val problem: String?,
)

/** Highest `actualizado_en` received per synced table ("products" | "clients" | "orders"). */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val table: String,
    val lastSync: String,
)

/** Order header plus its items, for the detail screen. */
data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val items: List<OrderItemEntity>,
)

/**
 * Item joined with its product, so the detail can show "3 × Arroz Grano de Oro 1 kg".
 * `productName` is null when the product is missing from the local catalog.
 */
data class OrderItemRow(
    @Embedded val item: OrderItemEntity,
    val productName: String?,
    val productUnit: String?,
)

/**
 * Row of the orders list: header plus the client name (null when the client is
 * missing locally; the UI then falls back to `clientId`) and the item count.
 */
data class OrderSummary(
    @Embedded val order: OrderEntity,
    val clientName: String?,
    val itemCount: Int,
)
