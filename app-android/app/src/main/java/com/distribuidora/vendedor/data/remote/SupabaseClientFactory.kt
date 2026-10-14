package com.distribuidora.vendedor.data.remote

import com.distribuidora.vendedor.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds the single Supabase client of the app with the anon key from BuildConfig.
 * Auth persists the session in SharedPreferences, so after the first login the app
 * opens straight into Home even without network.
 */
object SupabaseClientFactory {
    fun create(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
}
