package com.spoonart1.blespoonart.core.service.config

import android.content.Context
import java.security.SecureRandom
import java.util.UUID
import androidx.core.content.edit

internal object Ble {
    val SERVICE_UUID: UUID = UUID.fromString("7a1c0001-5b0e-4c3a-9d6e-0a1b2c3d4e5f")

    const val COMPANY_ID = 0xFFFF

    const val ACTION_START = "com.spoonart.blespoonart.START"
    const val EXTRA_MODE = "mode"
    const val EXTRA_ADVERTISE = "advertise"
    const val EXTRA_SCAN = "scan"
    const val EXTRA_WAKELOCK = "wakelock"

    /** Peer considered LOST if not seen for this long. */
    const val LOST_TIMEOUT_MS = 15_000L
    const val HEARTBEAT_MS = 60_000L

    val MODE_NAMES = arrayOf("LOW_POWER", "BALANCED", "LOW_LATENCY")

    /** Random id generated once per install so peers can be told apart despite MAC rotation. */
    fun localId(ctx: Context): ByteArray {
        val prefs = ctx.getSharedPreferences("ble", Context.MODE_PRIVATE)
        val saved = prefs.getString("id", null)
        if (saved != null) return saved.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val id = ByteArray(4).also { SecureRandom().nextBytes(it) }
        prefs.edit { putString("id", id.toHex()) }
        return id
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }