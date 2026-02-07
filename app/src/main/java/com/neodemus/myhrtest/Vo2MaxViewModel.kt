package com.neodemus.myhrtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

// Service UUID
val VO2MAX_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0")

// Separate Characteristic UUIDs
val VO2_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef1")
val VCO2_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef2")
val RQ_CHAR_UUID: UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef3")

val CLIENT_CHARACTERISTIC_CONFIG_UUID_VO2MAX: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
class Vo2MaxViewModel(application: Application) : AndroidViewModel(application) {

    private val _vo2 = MutableStateFlow(0.0f)
    val vo2: StateFlow<Float> = _vo2

    private val _vco2 = MutableStateFlow(0.0f)
    val vco2: StateFlow<Float> = _vco2

    private val _rq = MutableStateFlow(0.0f)
    val rq: StateFlow<Float> = _rq

    private val _connectedDevice = MutableStateFlow("N/A")
    val connectedDevice: StateFlow<String> = _connectedDevice
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val bluetoothManager: BluetoothManager =
        application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    private fun hasPermission(permission: String): Boolean =
        ActivityCompat.checkSelfPermission(getApplication(), permission) == PackageManager.PERMISSION_GRANTED

    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            _connectionState.value = "No BLUETOOTH_SCAN permission"
            return
        }

        _connectionState.value = "Scanning for VO2Max..."
        val scanFilter = ScanFilter.Builder().setServiceUuid(ParcelUuid(VO2MAX_SERVICE_UUID)).build()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            bleScanner?.stopScan(this)
            _connectedDevice.value = result.device.name ?: "Unknown"
            _connectionState.value = "Device found, connecting..."
            result.device.connectGatt(getApplication(), false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected. Refreshing cache..."

                // Force refresh before discovery
                refreshDeviceCache(gatt)

                // Small delay often helps the hardware catch up after a refresh
                Handler(Looper.getMainLooper()).postDelayed({
                    gatt.discoverServices()
                }, 500)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                _vo2.value = 0.0f
                _vco2.value = 0.0f
                _rq.value = 0.0f
                gatt.close()
            }
        }
        private fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
            return try {
                val method = gatt.javaClass.getMethod("refresh")
                val success = method.invoke(gatt) as Boolean
                Log.d("BLE_CACHE", "GATT cache refresh successful: $success")
                success
            } catch (e: Exception) {
                Log.e("BLE_CACHE", "An exception occurred while refreshing device cache", e)
                false
            }
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(VO2MAX_SERVICE_UUID) ?: return

            // Subscribe to all three characteristics
            val characteristicUuids = listOf(VO2_CHAR_UUID, VCO2_CHAR_UUID, RQ_CHAR_UUID)

            characteristicUuids.forEach { uuid ->
                val characteristic = service.getCharacteristic(uuid)
                if (characteristic != null) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID_VO2MAX)
                    if (descriptor != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }
                }
            }
            _connectionState.value = "Subscribed to VO2, VCO2, and RQ"
        }

        // Modern API 33+ callback
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicUpdate(characteristic.uuid, value)
        }

        // Deprecated API < 33 callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicUpdate(characteristic.uuid, characteristic.value)
        }
    }

    /**
     * Handles the raw byte data from the three different characteristics.
     * Assuming the peripheral sends a Float (4 bytes) in Little Endian format.
     */
    private fun handleCharacteristicUpdate(uuid: UUID, data: ByteArray) {
        if (data.size < 4) return // Ensure we have enough bytes for a float

        // Parse 4 bytes into a Float (IEEE 754)
        val value = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).float

        viewModelScope.launch {
            when (uuid) {
                VO2_CHAR_UUID -> _vo2.emit(value)
                VCO2_CHAR_UUID -> _vco2.emit(value)
                RQ_CHAR_UUID -> _rq.emit(value)
            }
        }
        Log.d("VO2MAX", "UUID: $uuid, Value: $value")
    }
}