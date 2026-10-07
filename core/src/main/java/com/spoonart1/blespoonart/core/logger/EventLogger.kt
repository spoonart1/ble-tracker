package com.spoonart1.blespoonart.core.logger

import android.app.KeyguardManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import javax.inject.Inject

object LiveLog {
    private val lines = ArrayDeque<String>()
    @Volatile var status: Status = Status.Idle

    @Synchronized fun push(line: String) {
        lines.addFirst(line)
        while (lines.size > 150) lines.removeLast()
    }

    @Synchronized fun snapshot(): String = lines.joinToString("\n")
}

class EventLogger @Inject constructor(
    @param:ApplicationContext private val ctx: Context
) {
    val file: File
    private val writer: BufferedWriter
    private val startMs = SystemClock.elapsedRealtime()
    private val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    @Volatile var modeName: String = ""

    init {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        file = File(dir, "ble_log_$ts.csv")
        writer = file.bufferedWriter()
        writer.write("ts_ms,elapsed_s,event,peer,rssi,gap_ms,screen_on,locked,mode,battery_pct,charge_uah,note\n")
        writer.flush()
    }

    @Synchronized
    fun log(event: String, peer: String = "", rssi: Int? = null, gapMs: Long? = null, note: String = "") {
        val now = System.currentTimeMillis()
        val elapsed = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        val screenOn = pm.isInteractive
        val locked = km.isKeyguardLocked
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) // microamp-hours
        val safeNote = note.replace(',', ';').replace('\n', ' ')
        writer.write(
            "$now,${"%.1f".format(Locale.US, elapsed)},$event,$peer,${rssi ?: ""},${gapMs ?: ""}," +
                    "${if (screenOn) 1 else 0},${if (locked) 1 else 0},$modeName,$pct,$charge,$safeNote\n"
        )
        writer.flush()
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(now))
        LiveLog.push("$t $event $peer ${rssi ?: ""} ${gapMs?.let { "gap=${it}ms" } ?: ""} $safeNote".trim())
    }

    @Synchronized fun close() {
        try { writer.close() } catch (_: Exception) {}
    }

    companion object {
        fun latestLog(ctx: Context): File? {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            return dir.listFiles { f -> f.name.startsWith("ble_log_") && f.name.endsWith(".csv") }
                ?.maxByOrNull { it.lastModified() }
        }
    }
}