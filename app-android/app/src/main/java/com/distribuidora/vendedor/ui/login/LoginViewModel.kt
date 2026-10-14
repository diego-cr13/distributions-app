package com.distribuidora.vendedor.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distribuidora.vendedor.data.repository.AuthRepository
import com.distribuidora.vendedor.data.repository.LoginException
import com.distribuidora.vendedor.data.repository.SyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val code: String = "",
    val password: String = "",
    val loading: Boolean = false,
    /** True while the initial catalog download runs ("Descargando catálogo…"). */
    val syncing: Boolean = false,
    @param:StringRes val errorRes: Int? = null,
    val errorDetail: String? = null,
) {
    val canSubmit: Boolean get() = !loading && !syncing && code.isNotBlank() && password.isNotEmpty()
}

class LoginViewModel(
    private val auth: AuthRepository,
    private val sync: SyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onCodeChange(value: String) = _state.update { it.copy(code = value, errorRes = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, errorRes = null) }

    /**
     * Login, then the initial sync. When both finish (or the sync fails, which Home
     * reports as "No se pudo sincronizar") the login phase goes Idle and AppRoot shows Home.
     */
    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, errorRes = null, errorDetail = null) }
        viewModelScope.launch {
            try {
                auth.login(current.code, current.password)
            } catch (e: LoginException) {
                _state.update { it.copy(loading = false, errorRes = e.messageRes, errorDetail = e.detail) }
                return@launch
            }
            _state.update { it.copy(loading = false, syncing = true, password = "") }
            try {
                sync.syncAll()
            } catch (_: Exception) {
                // Recorded in SyncRepository.status; Home shows the notice.
            } finally {
                // Fresh form for the next login (the ViewModel outlives the screen).
                _state.value = LoginUiState()
                auth.finishLogin()
            }
        }
    }
}
