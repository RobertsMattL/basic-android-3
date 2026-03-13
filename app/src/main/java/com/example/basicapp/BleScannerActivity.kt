package com.example.basicapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BleScannerActivity : AppCompatActivity() {

    private lateinit var deviceListView: ListView
    private lateinit var statusTextView: TextView
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<String>()
    private val deviceAddresses = mutableSetOf<String>()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var isScanning = false

    companion object {
        private const val REQUEST_PERMISSION_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scanner)

        deviceListView = findViewById(R.id.deviceListView)
        statusTextView = findViewById(R.id.statusTextView)

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        deviceListView.adapter = deviceAdapter

        // Initialize Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        // Check and request permissions
        if (checkPermissions()) {
            startBleScan()
        } else {
            requestPermissions()
        }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBleScan()
            } else {
                Toast.makeText(this, "Permissions required for BLE scanning", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startBleScan() {
        if (isScanning) return

        if (!checkPermissions()) {
            Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
            return
        }

        deviceList.clear()
        deviceAddresses.clear()
        deviceAdapter.notifyDataSetChanged()

        statusTextView.text = "Scanning for BLE devices..."

        try {
            bleScanner?.startScan(scanCallback)
            isScanning = true
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopBleScan() {
        if (!isScanning) return

        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            statusTextView.text = "Scan complete. Found ${deviceList.size} devices."
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)

            result?.device?.let { device ->
                if (!checkPermissions()) return

                try {
                    val address = device.address
                    if (!deviceAddresses.contains(address)) {
                        deviceAddresses.add(address)

                        val deviceName = device.name ?: "Unknown Device"
                        val rssi = result.rssi
                        val deviceInfo = "$deviceName\n$address (RSSI: $rssi dBm)"

                        runOnUiThread {
                            deviceList.add(deviceInfo)
                            deviceAdapter.notifyDataSetChanged()
                            statusTextView.text = "Found ${deviceList.size} devices..."
                        }
                    }
                } catch (e: SecurityException) {
                    runOnUiThread {
                        Toast.makeText(this@BleScannerActivity, "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                statusTextView.text = "Scan failed with error code: $errorCode"
                isScanning = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
    }
}
