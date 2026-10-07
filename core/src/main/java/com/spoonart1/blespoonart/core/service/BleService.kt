package com.spoonart1.blespoonart.core.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.spoonart1.blespoonart.core.logger.LiveLog
import com.spoonart1.blespoonart.core.logger.Status
import com.spoonart1.blespoonart.core.service.config.Ble
import com.spoonart1.blespoonart.core.service.controller.BleController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that advertises our service UUID + id and scans for the same UUID.
 * A foreground service is the only reliable way to keep BLE scanning alive with the screen off
 * on modern Android; the scan MUST carry a ScanFilter or Android silently stops delivering
 * results once the screen turns off.
 */
@AndroidEntryPoint
@SuppressLint("MissingPermission")
class BleService : Service() {

    @Inject
    lateinit var ble: BleController

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionManager: BleSessionManager? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if ((intent?.action != Ble.ACTION_START) || running) return START_NOT_STICKY
        running = true

        val mode = (intent.getIntExtra(Ble.EXTRA_MODE, 1)).coerceIn(0, 2)
        val doAdvertise = intent.getBooleanExtra(Ble.EXTRA_ADVERTISE, true)
        val doScan = intent.getBooleanExtra(Ble.EXTRA_SCAN, true)
        val useWakeLock = intent.getBooleanExtra(Ble.EXTRA_WAKELOCK, false)

        startInForeground()

        if (useWakeLock) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ble:lab").apply { acquire() }
        }

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val manager = BleSessionManager(ble)
        sessionManager = manager

        val started = manager.startSession(
            scope = serviceScope,
            context = this,
            adapter = adapter,
            mode = mode,
            doAdvertise = doAdvertise,
            doScan = doScan,
            useWakeLock = useWakeLock,
        )

        if (!started) {
            wakeLock?.let { if (it.isHeld) it.release() }
            running = false
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel("ble", "BLE test", NotificationManager.IMPORTANCE_LOW)
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pi = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, "ble")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("BLE proximity test running")
            .setContentText("Advertising and scanning")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        val type =
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, 1, n, type)
        } catch (e: SecurityException) {
            LiveLog.status = Status.Custom("Foreground Service permission error (${e.message}): Please allow required permissions in App Info Settings.")
        } catch (e: Exception) {
            LiveLog.status = Status.Custom("Failed to start foreground service: ${e.message}")
        }
    }

    override fun onDestroy() {
        sessionManager?.stopSession()
        sessionManager = null
        serviceScope.cancel()

        wakeLock?.let { if (it.isHeld) it.release() }
        running = false
        super.onDestroy()
    }
}