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
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

// Standard BLE Service and Characteristic UUIDs for Heart Rate
val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


@SuppressLint("MissingPermission")
class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate

    private val _connectedDevice = MutableStateFlow("N/A")
    val connectedDevice: StateFlow<String> = _connectedDevice
    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val bluetoothManager: BluetoothManager =
        application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            _connectionState.value = "No BLUETOOTH_SCAN permission"
            return
        }

        _connectionState.value = "Scanning..."
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                _connectionState.value = "Scan stopped, missing permission"
                return
            }
            bleScanner?.stopScan(this)
            _connectedDevice.value = result.device.name
            _connectionState.value = "Device found, connecting..."
            result.device.connectGatt(getApplication(), false, gattCallback)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = "Scan Failed: Code $errorCode"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                _connectionState.value = "Connect failed, missing permission"
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Connected! Discovering services..."
                this@HeartRateViewModel.gatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                _heartRate.value = 0
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(HEART_RATE_SERVICE_UUID)
            val characteristic = service?.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID)
            if (characteristic != null) {
                gatt.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                _connectionState.value = "Subscribed to HR"
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                // Parse the heart rate value from the characteristic
                val flag = characteristic.properties
                val format =
                    if ((flag and 0x01) != 0) BluetoothGattCharacteristic.FORMAT_UINT16 else BluetoothGattCharacteristic.FORMAT_UINT8
                val heartRateValue = characteristic.getIntValue(format, 1)
                viewModelScope.launch {
                    _heartRate.emit(heartRateValue)
                }
            }
        }
    }
}