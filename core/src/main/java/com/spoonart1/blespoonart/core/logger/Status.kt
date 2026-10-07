package com.spoonart1.blespoonart.core.logger

sealed class Status {
    object Idle : Status()
    object BluetoothOff: Status()
    data class Custom(val message: String) : Status()
}