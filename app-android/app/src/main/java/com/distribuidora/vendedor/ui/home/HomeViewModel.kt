package com.distribuidora.vendedor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.SessionStore
import com.distribuidora.vendedor.core.VendorInfo
import com.distribuidora.vendedor.data.repository.AuthRepository
import com.distribuidora.vendedor.data.repository.OrderRepository
import com.distribuidora.vendedor.data.repository.SyncError
import com.distribuidora.vendedor.data.repository.SyncRepository
import com.distribuidora.vendedor.data.repository.SyncStatus
import com.distribuidora.vendedor.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Tabs of the Home screen. */
enum class HomeTab { Orders, NewOrder, Catalog, Clients }

class HomeViewModel(
    private val auth: AuthRepository,
    private val orders: OrderRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    sessionStore: SessionStore,
) : ViewModel() {
    val vendor: StateFlow<VendorInfo?> = sessionStore.vendor

    /** Outcome of the last sync (initial download, worker or "Sincronizar ahora"). */
    val syncStatus: StateFlow<SyncStatus> = sync.status

    private val _message = MutableStateFlow<Int?>(null)

    /** One-shot notice for the snackbar (e.g. "Sincroniza antes de salir"). */
    val message: StateFlow<Int?> = _message

    /** Entering Home: background sync now and make sure the 15-minute periodic run exists. */
    fun onEnter() {
        scheduler.enqueueNow()
        scheduler.ensurePeriodic()
    }

    /** "Sincronizar ahora": direct call for immediate feedback; the worker covers the background. */
    fun syncNow() {
        if (syncStatus.value is SyncStatus.Running) return
        viewModelScope.launch {
            try {
                sync.syncAll()
            } catch (_: SyncError) {
                // Recorded in syncStatus; an Auth error already forced the logout.
            }
        }
    }

    fun clearSyncStatus() = sync.clearStatus()

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * "Salir": blocked while there are pending orders (they would be sent later under
     * another vendor's session); otherwise clears local orders and signs out.
     */
    fun logout() {
        viewModelScope.launch {
            if (orders.pendingCount() > 0) {
                _message.value = R.string.logout_blocked
            } else {
                auth.logout()
            }
        }
    }
}
