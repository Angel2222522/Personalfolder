package com.angel.personalfolder.security

import android.content.Context
import org.json.JSONObject

/**
 * Durable, non-sensitive marker for the startup recovery state.
 *
 * The marker is intentionally separate from the library database. If opening
 * Room itself is part of the failure, the application can still tell the user
 * that normal mutations are blocked instead of silently proceeding.
 */
object StartupRecoveryStateStore {
    private const val FILE_NAME = "startup_recovery_state.json"

    enum class Status { IN_PROGRESS, SAFE, BLOCKED }

    data class Snapshot(val status: Status, val message: String? = null)

    fun markInProgress(context: Context) = write(context, Snapshot(Status.IN_PROGRESS))

    fun markSafe(context: Context) = write(context, Snapshot(Status.SAFE))

    fun markBlocked(context: Context, message: String) = write(
        context,
        Snapshot(Status.BLOCKED, message.take(500))
    )

    fun read(context: Context): Snapshot? {
        val file = context.filesDir.resolve(FILE_NAME)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            Snapshot(
                status = Status.valueOf(json.getString("status")),
                message = json.optString("message").ifBlank { null }
            )
        }.getOrNull()
    }

    private fun write(context: Context, snapshot: Snapshot) {
        val target = context.filesDir.resolve(FILE_NAME)
        val temporary = context.filesDir.resolve(".$FILE_NAME.${System.nanoTime()}.part")
        try {
            temporary.outputStream().use { output ->
                output.write(
                    JSONObject()
                        .put("status", snapshot.status.name)
                        .put("message", snapshot.message ?: JSONObject.NULL)
                        .put("updatedAt", System.currentTimeMillis())
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
                output.fd.sync()
            }
            require(temporary.renameTo(target)) { "Δεν ήταν δυνατή η αποθήκευση της κατάστασης ανάκτησης." }
        } finally {
            temporary.delete()
        }
    }
}
