package com.distribuidora.vendedor

import android.app.Application
import android.content.Context
import com.distribuidora.vendedor.core.SessionStore
import com.distribuidora.vendedor.data.local.AppDatabase
import com.distribuidora.vendedor.data.remote.SupabaseClientFactory
import com.distribuidora.vendedor.data.repository.AuthRepository
import com.distribuidora.vendedor.data.repository.OrderRepository
import com.distribuidora.vendedor.data.repository.SyncRepository
import com.distribuidora.vendedor.sync.SyncScheduler
import io.github.jan.supabase.SupabaseClient

/** Application entry point: wires the dependency graph once per process. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/**
 * Hand-rolled dependency graph (no Hilt). Everything is created lazily from the
 * application context, so the worker and the UI share the same instances.
 */
object Graph {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val db: AppDatabase by lazy { AppDatabase.build(appContext) }
    val sessionStore: SessionStore by lazy { SessionStore(appContext) }
    val supabase: SupabaseClient by lazy { SupabaseClientFactory.create() }
    val orderRepository: OrderRepository by lazy { OrderRepository(db) }
    val authRepository: AuthRepository by lazy { AuthRepository(supabase, orderRepository, sessionStore) }
    val syncRepository: SyncRepository by lazy { SyncRepository(supabase, db, authRepository) }
    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }
}
