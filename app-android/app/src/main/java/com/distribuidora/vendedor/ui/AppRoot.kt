package com.distribuidora.vendedor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.distribuidora.vendedor.Graph
import com.distribuidora.vendedor.data.repository.LoginPhase
import com.distribuidora.vendedor.ui.home.HomeScreen
import com.distribuidora.vendedor.ui.login.LoginScreen
import io.github.jan.supabase.auth.status.SessionStatus

/** Top-level screens; no navigation library, just state. */
enum class Screen { Login, Home }

/**
 * Picks Login or Home from the Supabase session status:
 * Authenticated → Home, NotAuthenticated → Login, Initializing → blank,
 * RefreshFailure (e.g. no network for days) → stays where it is, defaulting to Home
 * because a stored session exists. During an interactive login (profile check and
 * initial download) the Login screen stays on top regardless of the session status.
 */
@Composable
fun AppRoot() {
    val status by Graph.authRepository.sessionStatus.collectAsStateWithLifecycle()
    val loginPhase by Graph.authRepository.loginPhase.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf<Screen?>(null) }

    when (status) {
        is SessionStatus.Authenticated -> screen = Screen.Home
        is SessionStatus.NotAuthenticated -> screen = Screen.Login
        is SessionStatus.RefreshFailure -> if (screen == null) screen = Screen.Home
        SessionStatus.Initializing -> Unit
    }
    val effective = if (loginPhase != LoginPhase.Idle) Screen.Login else screen

    Surface(modifier = Modifier.fillMaxSize()) {
        when (effective) {
            Screen.Login -> LoginScreen()
            Screen.Home -> HomeScreen()
            null -> Box(modifier = Modifier.fillMaxSize())
        }
    }
}
