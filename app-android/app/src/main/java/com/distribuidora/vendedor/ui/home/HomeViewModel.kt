package com.distribuidora.vendedor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.core.SessionStore
import com.distribuidora.vendedor.core.VendorInfo
import com.distribuidora.vendedor.data.repository.AuthRepository
import com.distribuidora.vendedor.data.repository.SyncError
import com.distribuidora.vendedor.data.repository.SyncRepository
import com.distribuidora.vendedor.data.repository.SyncStatus
import com.distribuidora.vendedor.sync.SyncScheduler
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Tabs of the Home screen. */
enum class HomeTab { Orders, NewOrder, Catalog, Clients }

class HomeViewModel(
    private val auth: AuthRepository,
    private val sync: SyncRepository,
    private val scheduler: SyncScheduler,
    sessionStore: SessionStore,
) : ViewModel() {
    val vendor: StateFlow<VendorInfo?> = sessionStore.vendor

    /** Outcome of the last sync (initial download, worker or "Sincronizar ahora"). */
    val syncStatus: StateFlow<SyncStatus> = sync.status

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
                // Recorded in syncStatus; the screen shows the message.
            }
        }
    }

    fun clearSyncStatus() = sync.clearStatus()

    /** "Salir": clears local orders and signs out; AppRoot then shows Login. */
    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
