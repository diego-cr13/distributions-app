package com.distribuidora.vendedor.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    /** Catalog filtered by code or name; empty query returns everything. */
    @Query(
        """
        SELECT * FROM products
        WHERE :query = '' OR code LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'
        ORDER BY code
        """
    )
    fun observe(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int
}

@Dao
interface ClientDao {
    @Upsert
    suspend fun upsertAll(clients: List<ClientEntity>)

    /** Clients filtered by code or name; empty query returns everything. */
    @Query(
        """
        SELECT * FROM clients
        WHERE :query = '' OR code LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%'
        ORDER BY code
        """
    )
    fun observe(query: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun getById(id: String): ClientEntity?

    @Query("SELECT COUNT(*) FROM clients")
    suspend fun count(): Int
}

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(order: OrderEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Upsert
    suspend fun upsertOrder(order: OrderEntity)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItems(orderId: String)

    /** Creates a local order with its items atomically. */
    @Transaction
    suspend fun insertOrderWithItems(order: OrderEntity, items: List<OrderItemEntity>) {
        insertOrder(order)
        insertItems(items)
    }

    /**
     * Server always wins: replaces the header (even a local `pendiente`) and swaps
     * the items for the ones the server returned, in one transaction.
     */
    @Transaction
    suspend fun replaceFromServer(order: OrderEntity, items: List<OrderItemEntity>) {
        upsertOrder(order)
        deleteItems(order.id)
        insertItems(items)
    }

    /** All local orders: pending first, then newest first. */
    @Transaction
    @Query(
        """
        SELECT o.*, c.name AS clientName,
               (SELECT COUNT(*) FROM order_items i WHERE i.orderId = o.id) AS itemCount
        FROM orders o
        LEFT JOIN clients c ON c.id = o.clientId
        ORDER BY CASE WHEN o.status = 'pendiente' THEN 0 ELSE 1 END, o.createdOnDevice DESC
        """
    )
    fun observeSummaries(): Flow<List<OrderSummary>>

    /** One order with client name and item count, for the detail header. */
    @Transaction
    @Query(
        """
        SELECT o.*, c.name AS clientName,
               (SELECT COUNT(*) FROM order_items i WHERE i.orderId = o.id) AS itemCount
        FROM orders o
        LEFT JOIN clients c ON c.id = o.clientId
        WHERE o.id = :id
        """
    )
    fun observeSummary(id: String): Flow<OrderSummary?>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'pendiente'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM orders WHERE status = 'pendiente'")
    suspend fun pendingCount(): Int

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeWithItems(id: String): Flow<OrderWithItems?>

    @Query(
        """
        SELECT i.*, p.name AS productName, p.unit AS productUnit
        FROM order_items i
        LEFT JOIN products p ON p.id = i.productId
        WHERE i.orderId = :orderId
        ORDER BY p.code
        """
    )
    fun observeItemRows(orderId: String): Flow<List<OrderItemRow>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE status = 'pendiente' ORDER BY createdOnDevice")
    suspend fun getPending(): List<OrderEntity>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItems(orderId: String): List<OrderItemEntity>

    @Query(
        """
        UPDATE orders SET attempts = attempts + 1, lastError = :message
        WHERE id = :id AND status = 'pendiente'
        """
    )
    suspend fun markSendFailed(id: String, message: String)

    @Query("DELETE FROM order_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    /** Logout: wipes every local order and its items. */
    @Transaction
    suspend fun clearAll() {
        deleteAllItems()
        deleteAllOrders()
    }
}

@Dao
interface SyncMetaDao {
    @Query("SELECT lastSync FROM sync_meta WHERE `table` = :table")
    suspend fun lastSync(table: String): String?

    @Upsert
    suspend fun upsert(meta: SyncMetaEntity)

    @Query("DELETE FROM sync_meta WHERE `table` = :table")
    suspend fun delete(table: String)
}
