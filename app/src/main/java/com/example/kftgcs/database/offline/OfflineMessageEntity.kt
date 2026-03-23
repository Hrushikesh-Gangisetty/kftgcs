package com.example.kftgcs.database.offline

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Stores outgoing WebSocket messages that could not be sent due to no internet.
 * Flushed to the backend when connectivity is restored and the session is re-established.
 *
 * Queued message types: mission_status, mission_event, mission_summary
 * Telemetry is NOT queued (high-frequency; already persisted via TlogRepository).
 *
 * Fields
 * ------
 * clientId    — UUID stamped at enqueue time; lets the backend deduplicate if the same
 *               message is delivered twice (e.g. flush race between WSM and SyncWorker).
 * retryCount  — incremented every time a flush attempt fails for this row.
 *               Rows at MAX_RETRIES are skipped and eventually garbage-collected.
 * status      — PENDING (default) | FAILED (exhausted retries, kept for diagnostics)
 */
@Entity(tableName = "offline_messages")
data class OfflineMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val messageType: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_FAILED  = "FAILED"
        const val MAX_RETRIES    = 5
    }
}
