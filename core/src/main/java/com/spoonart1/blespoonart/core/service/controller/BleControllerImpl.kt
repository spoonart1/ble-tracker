package com.spoonart1.blespoonart.core.service.controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.spoonart1.blespoonart.core.logger.EventLogger
import com.spoonart1.blespoonart.core.logger.LiveLog
import com.spoonart1.blespoonart.core.logger.Status
import com.spoonart1.blespoonart.core.service.config.Ble
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BleControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val advertiser: BluetoothLeAdvertiser?,
    private val scanner: BluetoothLeScanner?,
    private val logger: EventLogger,
) : BleController {

    override fun startAdvertising(
        adapter: BluetoothAdapter,
        mode: Int,
        callback: AdvertiseCallback
    ) {
        if (advertiser == null) {
            logger.log("ERROR", note = "advertising not supported")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(mode) // 0 low power (1000ms), 1 balanced (250ms), 2 low latency (100ms)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(Ble.SERVICE_UUID))
            .addManufacturerData(Ble.COMPANY_ID, Ble.localId(context))
            .build()

        try {
            advertiser.startAdvertising(settings, data, callback)
        } catch (e: SecurityException) {
            logger.log("ERROR", note = "advertising permission revoked: ${e.message}")
            LiveLog.status = Status.Custom("Permission revoked: Please allow required Bluetooth permissions in App Info Settings.")
        } catch (e: Exception) {
            logger.log("ERROR", note = "advertising failed to start: ${e.message}")
        }
    }

    override fun startScanning(
        adapter: BluetoothAdapter,
        mode: Int,
        callback: ScanCallback
    ) {
        if (scanner == null) {
            logger.log("ERROR", note = "scanner unavailable")
            return
        }
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(Ble.SERVICE_UUID)).build())
        val settings = ScanSettings.Builder()
            .setScanMode(mode) // 0 low power, 1 balanced, 2 low latency
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0)
            .build()
        try {
            scanner.startScan(filters, settings, callback)
            logger.log("SCAN_STARTED")
        } catch (e: SecurityException) {
            logger.log("ERROR", note = "scanning permission revoked: ${e.message}")
            LiveLog.status = Status.Custom("Permission revoked: Please allow required Bluetooth/Location permissions in App Info Settings.")
        } catch (e: Exception) {
            logger.log("ERROR", note = "scanning failed to start: ${e.message}")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    override fun stopAdvertising(callback: AdvertiseCallback) {
        try {
            advertiser?.stopAdvertising(callback)
        } catch (_: Exception) {
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun stopScan(callback: ScanCallback) {
        try {
            scanner?.stopScan(callback)
        } catch (_: Exception) {
        }
    }
}