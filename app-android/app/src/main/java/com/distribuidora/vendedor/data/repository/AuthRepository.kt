package com.distribuidora.vendedor.data.repository

import androidx.annotation.StringRes
import com.distribuidora.vendedor.R
import com.distribuidora.vendedor.core.SessionStore
import com.distribuidora.vendedor.data.remote.ProfileDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.postgrest.from
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Login failure with the message resource the screen shows. */
class LoginException(@param:StringRes val messageRes: Int, val detail: String? = null) : Exception(detail)

/**
 * Phase of an interactive login. While it is not [Idle], AppRoot keeps the Login
 * screen on top even though the Supabase session is already Authenticated, so the
 * profile check and the initial catalog download happen before Home appears.
 */
enum class LoginPhase { Idle, SigningIn, Syncing }

/**
 * RF-1: login with vendor code + password, session persistence and logout.
 * Only an active vendor may stay logged in; supervisors and inactive accounts are
 * signed out right away.
 */
class AuthRepository(
    private val supabase: SupabaseClient,
    private val orders: OrderRepository,
    private val sessionStore: SessionStore,
) {
    /** Authenticated / NotAuthenticated / RefreshFailure / Initializing, driven by supabase-kt. */
    val sessionStatus: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus

    private val _loginPhase = MutableStateFlow(LoginPhase.Idle)
    val loginPhase: StateFlow<LoginPhase> = _loginPhase

    private val _loginNotice = MutableStateFlow<Int?>(null)

    /** Message resource the Login screen shows after a forced logout ("Tu cuenta no está activa"). */
    val loginNotice: StateFlow<Int?> = _loginNotice

    /** Called by the login flow once the initial sync finished (or failed). */
    fun finishLogin() {
        _loginPhase.value = LoginPhase.Idle
    }

    /** Suspends until the stored session (if any) has been loaded. */
    suspend fun awaitInitialization() = supabase.auth.awaitInitialization()

    /** True when a session (even one whose refresh failed) is loaded. */
    fun hasSession(): Boolean = supabase.auth.currentSessionOrNull() != null

    /** Vendor code → login email of the seed (`V-01` → `v-01@distribuidora.test`). */
    fun emailFor(code: String): String = code.trim().lowercase() + EMAIL_DOMAIN

    /**
     * Signs in and checks the own profile. Throws [LoginException] with the message
     * to display: no network, bad credentials, or not an active vendor.
     */
    suspend fun login(code: String, password: String) {
        _loginPhase.value = LoginPhase.SigningIn
        _loginNotice.value = null
        try {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = emailFor(code)
                    this.password = password
                }
            } catch (e: AuthRestException) {
                throw LoginException(R.string.login_error_invalid_credentials, e.message)
            } catch (e: Exception) {
                throw LoginException(e.toLoginMessageRes(), e.message)
            }

            val profile = try {
                fetchOwnProfile()
            } catch (e: Exception) {
                signOutQuietly()
                throw LoginException(e.toLoginMessageRes(), e.message)
            }

            // RLS hides inactive profiles (no row) and the role check excludes supervisors.
            if (profile == null || !profile.isVendor) {
                signOutQuietly()
                throw LoginException(R.string.login_error_not_vendor)
            }
            sessionStore.save(profile.codigo, profile.nombre)
            // Stay on the Login screen until the caller runs the initial sync.
            _loginPhase.value = LoginPhase.Syncing
        } catch (e: LoginException) {
            _loginPhase.value = LoginPhase.Idle
            throw e
        }
    }

    /**
     * Own `perfiles` row, or null when RLS returns nothing (inactive account).
     * Network and PostgREST errors propagate.
     */
    suspend fun fetchOwnProfile(): ProfileDto? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return supabase.from("perfiles")
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeSingleOrNull<ProfileDto>()
    }

    /**
     * "Salir": wipes local orders (so another vendor on the same phone never sees
     * or sends them), the orders sync marker and the session store, then signs out.
     * Catalog and clients are kept. Callers must check there are no pending orders.
     */
    suspend fun logout() {
        orders.clearLocalOrders()
        sessionStore.clear()
        signOutQuietly()
    }

    /**
     * Forced logout when the account is no longer valid (HTTP 401/403 or no profile
     * row): local orders are kept so they survive until the vendor is reactivated.
     * The Login screen then shows "Tu cuenta no está activa".
     */
    suspend fun forceLogout() {
        _loginNotice.value = R.string.login_error_inactive
        sessionStore.clear()
        signOutQuietly()
    }

    /** Signs out even without network: the local session is always cleared. */
    private suspend fun signOutQuietly() {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            supabase.auth.clearSession()
        }
    }

    private fun Exception.toLoginMessageRes(): Int = when (this) {
        is HttpRequestException, is HttpRequestTimeoutException, is IOException ->
            R.string.login_error_no_network
        else -> R.string.login_error_generic
    }

    private companion object {
        const val EMAIL_DOMAIN = "@distribuidora.test"
    }
}

/** Keys of `sync_meta.table`. */
object SyncTables {
    const val PRODUCTS = "products"
    const val CLIENTS = "clients"
    const val ORDERS = "orders"
}
