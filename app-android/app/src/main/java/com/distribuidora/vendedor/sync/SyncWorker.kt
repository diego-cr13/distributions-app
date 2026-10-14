package com.distribuidora.vendedor.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.distribuidora.vendedor.Graph
import com.distribuidora.vendedor.data.repository.SyncError

/**
 * RF-4 background sync. Waits for the stored session to load; without a session it
 * does nothing. Retries (with the request's backoff) only on network errors.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val auth = Graph.authRepository
        auth.awaitInitialization()
        if (!auth.hasSession()) {
            Log.i(TAG, "No session; nothing to sync")
            return Result.success()
        }
        return try {
            Graph.syncRepository.syncAll()
            Result.success()
        } catch (e: SyncError.Network) {
            Log.w(TAG, "Network error, will retry: ${e.message}")
            Result.retry()
        } catch (e: SyncError) {
            Log.w(TAG, "Sync failed: ${e.message}")
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
