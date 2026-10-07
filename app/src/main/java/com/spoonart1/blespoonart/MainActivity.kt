package com.spoonart1.blespoonart

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.spoonart1.blespoonart.core.logger.EventLogger
import com.spoonart1.blespoonart.core.service.BleService
import com.spoonart1.blespoonart.core.service.config.Ble
import com.spoonart1.blespoonart.core.service.config.toHex
import com.spoonart1.blespoonart.ui.theme.BLESpoonartTheme
import com.spoonart1.feature_ble.ui.BleDashboard
import com.spoonart1.feature_ble.ui.BleDashboardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: BleDashboardViewModel by viewModels()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filter { !it.value }.keys.filter {
                (Build.VERSION.SDK_INT < 33) || (it != Manifest.permission.POST_NOTIFICATIONS)
            }
            if (denied.isNotEmpty()) {
                val showRationale = denied.any { shouldShowRequestPermissionRationale(it) }
                val msg = if (showRationale) {
                    "Permissions required to run BLE proximity scan and advertise. Please grant required permissions."
                } else {
                    "Required permissions were denied or revoked. Please allow permissions in App Info Settings."
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.updateStatusMessage(msg)
            } else {
                maybeRequestBackgroundLocation()
            }
        }

    private val bgLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCaptureSafely()
            } else {
                val msg = "Background location permission is required for background BLE scanning. Please enable it in App Info Settings."
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.updateStatusMessage(msg)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            BLESpoonartTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BleDashboard(
                        modifier = Modifier.padding(innerPadding),
                        deviceId = "Local id: ${Ble.localId(this).toHex()}",
                        selectedModeIndex = uiState.selectedModeIndex,
                        isAdvertiseChecked = uiState.isAdvertiseChecked,
                        isScanChecked = uiState.isScanChecked,
                        isWakeLockChecked = uiState.isWakeLockChecked,
                        statusText = uiState.statusText,
                        logText = uiState.logText,
                        onModeSelected = { viewModel.setModeIndex(it) },
                        onAdvertiseChanged = { viewModel.setAdvertiseChecked(it) },
                        onScanChanged = { viewModel.setScanChecked(it) },
                        onWakeLockChanged = { viewModel.setWakeLockChecked(it) },
                        onStartClick = { ensurePermissionsThenStart() },
                        onStopClick = { stopService(Intent(this, BleService::class.java)) },
                        onBatteryClick = {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                        onExportClick = { exportLog() },
                    )
                }
            }
        }
    }

    private fun missingPermissions(): List<String> {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            p += Manifest.permission.BLUETOOTH_SCAN
            p += Manifest.permission.BLUETOOTH_ADVERTISE
            p += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            p += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        return p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    }

    private fun ensurePermissionsThenStart() {
        val missing = missingPermissions()
        if (missing.isEmpty()) {
            maybeRequestBackgroundLocation()
        } else {
            val permanentlyDenied = missing.all {
                !shouldShowRequestPermissionRationale(it) &&
                        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (permanentlyDenied) {
                val msg = "Required permissions are disabled. Please enable them in App Info Settings."
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.updateStatusMessage(msg)
            }
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun maybeRequestBackgroundLocation() {
        val needsBg = Build.VERSION.SDK_INT in 29..30 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        if (needsBg) bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else startCaptureSafely()
    }

    private fun startCaptureSafely() {
        try {
            val currentState = viewModel.uiState.value
            val i = Intent(this, BleService::class.java)
                .setAction(Ble.ACTION_START)
                .putExtra(Ble.EXTRA_MODE, currentState.selectedModeIndex)
                .putExtra(Ble.EXTRA_ADVERTISE, currentState.isAdvertiseChecked)
                .putExtra(Ble.EXTRA_SCAN, currentState.isScanChecked)
                .putExtra(Ble.EXTRA_WAKELOCK, currentState.isWakeLockChecked)
            ContextCompat.startForegroundService(this, i)
        } catch (e: SecurityException) {
            val msg = "Permission error (${e.message}): Please enable required permissions in App Info Settings."
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            viewModel.updateStatusMessage(msg)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start capture service: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportLog() {
        val f = EventLogger.latestLog(this)
        if (f == null) {
            Toast.makeText(this, "No log yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Export BLE log"))
    }
}