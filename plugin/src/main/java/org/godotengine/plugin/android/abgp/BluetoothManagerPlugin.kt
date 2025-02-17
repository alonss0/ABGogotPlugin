package org.godotengine.plugin.android.abgp

import android.Manifest
import android.bluetooth.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import androidx.core.app.ActivityCompat
import org.godotengine.godot.BuildConfig
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class BluetoothManagerPlugin(godot: Godot) : GodotPlugin(godot) {
    private val context: Context = activity!!.applicationContext
    private val mBluetoothManager: BluetoothManager = activity!!.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val mBluetoothAdapter = mBluetoothManager.adapter
    private var mBluetoothGatt: BluetoothGatt? = null
    private val bluetoothLeScanner: BluetoothLeScanner? = mBluetoothAdapter?.bluetoothLeScanner

    // Signals
    fun emitDeviceFound(name: String, address: String) {
        emitSignal("device_found", name, address)
    }

    fun emitDataReceived(data: ByteArray) {
        emitSignal("data_received", data)
    }

    override fun getPluginName() = BuildConfig.LIBRARY_PACKAGE_NAME

    @UsedByGodot
    override fun getPluginMethods(): List<String> {
        return listOf(
            "startScan",
            "stopScan",
            "connect",
            "disconnect"
        )
    }

    override fun getPluginSignals(): Set<SignalInfo> {
        return setOf(
            SignalInfo("_on_debug_message", String::class.java),
            SignalInfo("_on_device_found", Map::class.java),
            SignalInfo("_on_connection_status_change", String::class.java),
            SignalInfo("_on_scan_stopped", String::class.java)
        )
    }

    private var scanning = false
    private val handler = Handler()

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 10000

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            emitDeviceFound(result.device.name, result.device.address)
        }
    }

    //Plugin methods
    private fun startScan() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            bluetoothLeScanner?.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner?.stopScan(leScanCallback)
        }
    }

    fun stopScan() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scanning = false
        bluetoothLeScanner?.stopScan(leScanCallback)
    }

    fun connect(address: String) {
        val device = mBluetoothAdapter?.getRemoteDevice(address)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mBluetoothGatt = device?.connectGatt(context, false, gattCallback)
    }

    fun disconnect() {
        mBluetoothGatt?.let { gatt ->
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            gatt.disconnect()
            gatt.close()
        }
    }

    // GATT callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                gatt.discoverServices()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            emitDataReceived(characteristic.value)
        }
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

            activity?.requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 0)
            return false
        }
        return true
    }
}