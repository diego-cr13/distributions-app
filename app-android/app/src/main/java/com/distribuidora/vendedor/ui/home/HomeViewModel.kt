package com.distribuidora.vendedor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.core.SessionStore
import com.distribuidora.vendedor.core.VendorInfo
import com.distribuidora.vendedor.data.repository.AuthRepository
import com.distribuidora.vendedor.data.repository.SyncRepository
import com.distribuidora.vendedor.data.repository.SyncStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Tabs of the Home screen. */
enum class HomeTab { Orders, NewOrder, Catalog, Clients }

class HomeViewModel(
    private val auth: AuthRepository,
    private val sync: SyncRepository,
    sessionStore: SessionStore,
) : ViewModel() {
    val vendor: StateFlow<VendorInfo?> = sessionStore.vendor

    /** Outcome of the last sync (initial download, worker or "Sincronizar ahora"). */
    val syncStatus: StateFlow<SyncStatus> = sync.status

    fun clearSyncStatus() = sync.clearStatus()

    /** "Salir": clears local orders and signs out; AppRoot then shows Login. */
    fun logout() {
        viewModelScope.launch { auth.logout() }
    }
}
