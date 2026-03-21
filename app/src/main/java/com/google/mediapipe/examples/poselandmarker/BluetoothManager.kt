package com.google.mediapipe.examples.poselandmarker

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectJob: Job? = null

    private val _status: MutableStateFlow<Status> = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    sealed class Status {
        object IDLE : Status()
        object CONNECTING : Status()
        data class CONNECTED(val deviceName: String?, val address: String) : Status()
        data class ERROR(val message: String) : Status()
    }

    fun isBluetoothSupported(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getPairedDevices(): Set<android.bluetooth.BluetoothDevice>? {
        return adapter?.bondedDevices
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String) {
        connectJob?.cancel()

        connectJob = scope.launch {
            val btAdapter = adapter ?: run {
                _status.value = Status.ERROR("Bluetooth not supported on this device")
                return@launch
            }

            if (!btAdapter.isEnabled) {
                _status.value = Status.ERROR("Bluetooth is OFF. Turn it on first.")
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _status.value = Status.ERROR("Missing BLUETOOTH_CONNECT permission")
                return@launch
            }

            try {
                _status.value = Status.CONNECTING

                val device = btAdapter.getRemoteDevice(address)
                btAdapter.cancelDiscovery()

                val s = device.createRfcommSocketToServiceRecord(sppUuid)

                safeClose()
                socket = s

                s.connect()
                out = s.outputStream

                _status.value = Status.CONNECTED(device.name, device.address)

            } catch (e: SecurityException) {
                safeClose()
                _status.value = Status.ERROR("Bluetooth permission denied: ${e.message ?: "SecurityException"}")
            } catch (e: IOException) {
                safeClose()
                _status.value = Status.ERROR("Connect failed: ${e.message ?: "IOException"}")
            } catch (e: IllegalArgumentException) {
                safeClose()
                _status.value = Status.ERROR("Invalid Bluetooth address")
            } catch (e: Exception) {
                safeClose()
                _status.value = Status.ERROR("Connect failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun send(text: String) {
        scope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _status.value = Status.ERROR("Missing BLUETOOTH_CONNECT permission")
                return@launch
            }

            try {
                val output = out ?: run {
                    _status.value = Status.ERROR("Not connected")
                    return@launch
                }

                output.write(text.toByteArray())
                output.flush()

            } catch (e: SecurityException) {
                _status.value = Status.ERROR("Bluetooth permission denied: ${e.message ?: "SecurityException"}")
            } catch (e: IOException) {
                _status.value = Status.ERROR("Send failed: ${e.message ?: "IOException"}")
            } catch (e: Exception) {
                _status.value = Status.ERROR("Send failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        safeClose()
        _status.value = Status.IDLE
    }

    private fun safeClose() {
        try { out?.close() } catch (_: IOException) {}
        out = null
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}