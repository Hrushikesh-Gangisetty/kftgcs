package com.example.kftgcs.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kftgcs.database.MissionTemplateDatabase
import com.example.kftgcs.telemetry.WebSocketManager
import java.util.concurrent.TimeUnit

/**
 * Background safety-net worker that flushes any offline-queued messages.
 *
 * Scheduling
 * ----------
 * Runs as a PeriodicWorkRequest every 15 minutes (WorkManager minimum),
 * constrained to CONNECTED network. GCSApplication schedules it with
 * ExistingPeriodicWorkPolicy.KEEP so it is never duplicated.
 *
 * What it does
 * ------------
 * 1. Garbage-collects FAILED messages older than 7 days.
 * 2. If the WebSocket is already connected with a missionId, it calls
 *    syncPendingMessages() — the same path used after reconnect.
 * 3. If the socket is NOT connected but there are pending messages AND
 *    the pilot is logged in (pilotId > 0), it attempts a reconnect.
 *    The NetworkCallback may have fired before credentials were set;
 *    this worker acts as a fallback 15 min later.
 *
 * Idempotency
 * -----------
 * syncPendingMessages() is guarded by an AtomicBoolean so a concurrent call
 * from the WebSocket reconnect path and this worker cannot double-send the
 * same row.  Each row also carries a clientId UUID for backend dedup.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val wsm = WebSocketManager.getInstance()

        // ── Garbage-collect permanently failed messages older than 7 days ──
        try {
            val dao = MissionTemplateDatabase
                .getDatabase(applicationContext)
                .offlineMessageDao()
            val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            dao.deleteFailedOlderThan(sevenDaysAgo)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "GC of failed messages error: ${e.message}")
        }

        // ── Flush or reconnect ────────────────────────────────────────────────
        if (wsm.isConnected && wsm.missionId != null) {
            // Socket live — flush immediately
            android.util.Log.i(TAG, "SyncWorker: WebSocket connected — flushing pending messages")
            wsm.syncPendingMessages()
        } else {
            // Socket not connected — check if there are queued messages worth reconnecting for
            try {
                val dao = MissionTemplateDatabase
                    .getDatabase(applicationContext)
                    .offlineMessageDao()
                val pendingCount = dao.countPending()

                if (pendingCount > 0 && wsm.pilotId > 0 && !wsm.isConnected) {
                    android.util.Log.i(TAG,
                        "SyncWorker: $pendingCount pending message(s), attempting reconnect")
                    // Post connect on main thread (OkHttp listener uses main handler)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        wsm.connect()
                    }
                } else {
                    android.util.Log.d(TAG,
                        "SyncWorker: nothing to flush (pending=$pendingCount, " +
                        "pilotId=${wsm.pilotId}, connected=${wsm.isConnected})")
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "SyncWorker: error checking pending: ${e.message}")
            }
        }

        return Result.success()
    }

    companion object {
        const val TAG         = "SyncWorker"
        const val WORK_NAME   = "offline_sync_periodic"
    }
}
