package com.distribuidora.vendedor.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database `vendedor.db`, version 1. Migrations are out of scope for the MVP:
 * a schema change drops everything; the catalog is downloaded again and confirmed
 * orders come back from the server.
 */
@Database(
    entities = [
        ProductEntity::class,
        ClientEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        SyncMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun clientDao(): ClientDao
    abstract fun orderDao(): OrderDao
    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val NAME = "vendedor.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
