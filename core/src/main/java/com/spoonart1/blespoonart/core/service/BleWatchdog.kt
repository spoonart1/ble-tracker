package com.spoonart1.blespoonart.core.service

import com.spoonart1.blespoonart.core.logger.EventLogger
import com.spoonart1.blespoonart.core.service.config.Ble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically checks for lost peers in the BLE scan results and updates service status using Kotlin coroutines.
 */
internal class BleWatchdog(
    private val lastSeen: Map<String, Long>,
    private val lostPeers: MutableSet<String>,
    private val logger: EventLogger,
    private val onUpdateStatus: () -> Unit,
    private val intervalMs: Long = 5_000L,
    private val lostTimeoutMs: Long = Ble.LOST_TIMEOUT_MS
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                checkLostPeers()
                onUpdateStatus()
            }
        }
    }

    fun checkLostPeers() {
        val now = System.currentTimeMillis()
        for ((peer, t) in lastSeen) {
            if (now - t > lostTimeoutMs && lostPeers.add(peer)) {
                logger.log("LOST", peer, null, now - t)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
