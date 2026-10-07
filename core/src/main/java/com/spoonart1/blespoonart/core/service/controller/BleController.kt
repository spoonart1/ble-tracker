package com.spoonart1.blespoonart.core.service.controller

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.ScanCallback

interface BleController {
    fun startAdvertising(
        adapter: BluetoothAdapter,
        mode: Int,
        callback: AdvertiseCallback
    )

    fun startScanning(
        adapter: BluetoothAdapter,
        mode: Int,
        callback: ScanCallback
    )

    fun stopScan(callback: ScanCallback)

    fun stopAdvertising(callback: AdvertiseCallback)
}