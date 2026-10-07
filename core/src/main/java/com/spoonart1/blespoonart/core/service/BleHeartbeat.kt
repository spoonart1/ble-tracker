package com.spoonart1.blespoonart.core.service

import com.spoonart1.blespoonart.core.logger.EventLogger
import com.spoonart1.blespoonart.core.service.config.Ble
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically logs a heartbeat event and resets sightings in the current window using Kotlin coroutines.
 */
internal class BleHeartbeat(
    private val logger: EventLogger,
    private val intervalMs: Long = Ble.HEARTBEAT_MS,
    private val getSightingsInWindow: () -> Int,
    private val getPeerCount: () -> Int,
    private val onResetSightings: () -> Unit
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch {
            while (isActive) {
                delay(intervalMs)
                logHeartbeat()
            }
        }
    }

    private fun logHeartbeat() {
        val sightings = getSightingsInWindow()
        val peers = getPeerCount()
        logger.log("HEARTBEAT", note = "sightings_last_min=$sightings peers=$peers")
        onResetSightings()
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
