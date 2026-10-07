package com.spoonart1.blespoonart.core.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import com.spoonart1.blespoonart.core.logger.EventLogger
import com.spoonart1.blespoonart.core.logger.LiveLog
import com.spoonart1.blespoonart.core.logger.Status
import com.spoonart1.blespoonart.core.service.config.Ble
import com.spoonart1.blespoonart.core.service.config.toHex
import com.spoonart1.blespoonart.core.service.controller.BleController
import kotlinx.coroutines.CoroutineScope

/**
 * Manages BLE advertising, scanning, session logging, peer tracking, and watchdog/heartbeat tasks.
 */
@SuppressLint("MissingPermission")
internal class BleSessionManager(
    private val ble: BleController,
) {
    private lateinit var logger: EventLogger
    private var watchdog: BleWatchdog? = null
    private var heartbeat: BleHeartbeat? = null

    private val lastSeen = HashMap<String, Long>()
    private val lostPeers = HashSet<String>()
    private val sightingsPerPeer = HashMap<String, Int>()
    private var sightingsInWindow = 0
    private var running = false

    fun startSession(
        scope: CoroutineScope,
        context: Context,
        adapter: BluetoothAdapter?,
        mode: Int,
        doAdvertise: Boolean,
        doScan: Boolean,
        useWakeLock: Boolean = false,
    ): Boolean {
        if (running) return false
        running = true

        logger = EventLogger(context).also { it.modeName = Ble.MODE_NAMES[mode] }
        logger.log(
            "SESSION_START",
            note = "sdk=${Build.VERSION.SDK_INT} model=${Build.MODEL} adv=$doAdvertise scan=$doScan wakelock=$useWakeLock",
        )

        if ((adapter == null) || !adapter.isEnabled) {
            logger.log("ERROR", note = "bluetooth unavailable or off")
            LiveLog.status = Status.BluetoothOff
            running = false
            return false
        }

        if (doAdvertise) startAdvertising(adapter, mode)
        if (doScan) startScanning(adapter, mode)

        watchdog = BleWatchdog(
            lastSeen = lastSeen,
            lostPeers = lostPeers,
            logger = logger,
            onUpdateStatus = ::updateStatus,
        ).also { it.start(scope) }

        heartbeat = BleHeartbeat(
            logger = logger,
            getSightingsInWindow = { sightingsInWindow },
            getPeerCount = { lastSeen.size },
            onResetSightings = { sightingsInWindow = 0 },
        ).also { it.start(scope) }

        updateStatus()
        return true
    }

    private fun startAdvertising(
        adapter: BluetoothAdapter,
        mode: Int,
    ) {
        ble.startAdvertising(adapter, mode, advCallback)
    }

    private val advCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            logger.log("ADV_STARTED", note = "mode=${settingsInEffect.mode} tx=${settingsInEffect.txPowerLevel}")
        }

        override fun onStartFailure(errorCode: Int) {
            logger.log("ADV_FAILED", note = "code=$errorCode")
        }
    }

    private fun startScanning(
        adapter: BluetoothAdapter,
        mode: Int,
    ) {
        ble.startScanning(adapter, mode, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult,
        ) = handleResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handleResult)
        override fun onScanFailed(errorCode: Int) {
            logger.log("SCAN_FAILED", note = "code=$errorCode")
        }
    }

    private fun handleResult(r: ScanResult) {
        // Android peers: stable id in manufacturer data. iOS peers can't set it -> fall back to address
        // (iOS rotates its address roughly every 15 minutes, so treat those IDs as short-lived).
        val mfg = r.scanRecord?.getManufacturerSpecificData(Ble.COMPANY_ID)
        val peer = mfg?.toHex() ?: "addr:${r.device.address}"
        val now = System.currentTimeMillis()
        val prev = lastSeen[peer]
        lastSeen[peer] = now
        sightingsPerPeer[peer] = (sightingsPerPeer[peer] ?: 0) + 1
        sightingsInWindow++

        when {
            prev == null -> logger.log("FOUND", peer, r.rssi, 0, "first sighting")
            lostPeers.remove(peer) -> logger.log("FOUND", peer, r.rssi, now - prev, "re-found after loss")
            else -> logger.log("SIGHTING", peer, r.rssi, now - prev)
        }
    }

    private fun updateStatus() {
        val now = System.currentTimeMillis()
        val summary = lastSeen.entries.joinToString("\n") { (p, t) ->
            "  $p  last seen ${(now - t) / 1000}s ago  n=${sightingsPerPeer[p]}${if (p in lostPeers) "  [LOST]" else ""}"
        }
        val msg = "Running (${logger.modeName}) - peers: ${lastSeen.size}\nLog: ${logger.file.name}" +
                if (summary.isNotEmpty()) "\n$summary" else ""
        LiveLog.status = Status.Custom(msg)
    }

    fun stopSession() {
        if (!running) return
        watchdog?.stop()
        heartbeat?.stop()

        ble.stopScan(scanCallback)
        ble.stopAdvertising(advCallback)

        logger.log("SESSION_END", note = "peers=${lastSeen.size}")
        logger.close()

        running = false
        LiveLog.status = Status.Idle
    }
}