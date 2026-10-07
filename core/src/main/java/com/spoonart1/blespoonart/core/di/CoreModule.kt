package com.spoonart1.blespoonart.core.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import com.spoonart1.blespoonart.core.service.controller.BleController
import com.spoonart1.blespoonart.core.service.controller.BleControllerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindBleController(
        impl: BleControllerImpl
    ): BleController

    companion object {
        @Provides
        fun provideAdapter(
            @ApplicationContext context: Context
        ): BluetoothAdapter {
            return (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        }

        @Provides
        fun provideScanner(
            adapter: BluetoothAdapter
        ): BluetoothLeScanner? {
            return adapter.bluetoothLeScanner
        }

        @Provides
        fun provideAdvertiser(adapter: BluetoothAdapter): BluetoothLeAdvertiser? {
            return adapter.bluetoothLeAdvertiser
        }
    }
}