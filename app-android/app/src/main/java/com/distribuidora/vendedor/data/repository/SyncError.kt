package com.distribuidora.vendedor.data.repository

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException

/**
 * Classification of a sync failure, by exception type and HTTP status (PostgREST
 * maps `errcode` 42501 → 403 and 22023 / P0001 → 400):
 *
 * - [Network]: Ktor network error or timeout. Nothing changes; the worker retries.
 * - [Auth]: HTTP 401/403, or the own profile has no row (RLS hides inactive vendors).
 *   The caller forces a logout without deleting local orders.
 * - [Data]: any other PostgREST error (HTTP 400 and similar); [message] is the text
 *   the RPC raised, verbatim.
 */
sealed class SyncError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : SyncError(cause.message ?: "network error", cause)
    class Auth(message: String, cause: Throwable? = null) : SyncError(message, cause)
    class Data(message: String, cause: Throwable? = null) : SyncError(message, cause)

    companion object {
        fun from(e: Throwable): SyncError = when (e) {
            is SyncError -> e
            is HttpRequestException,
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException,
            is IOException -> Network(e)
            is RestException -> when (e.statusCode) {
                401, 403 -> Auth(e.error, e)
                else -> Data(e.error, e)
            }
            else -> Data(e.message ?: e.javaClass.simpleName, e)
        }
    }
}
