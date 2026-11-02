package com.lanchoneteshirley.impressao.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BluetoothSppPrinterTransport(context: Context) : PrinterTransport {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): PrinterConnection = withContext(Dispatchers.IO) {
        val bluetooth = adapter ?: error("Bluetooth nao disponivel neste aparelho.")
        require(bluetooth.isEnabled) { "Ative o Bluetooth." }
        val socket = bluetooth.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP_UUID)
        bluetooth.cancelDiscovery()
        try {
            socket.connect()
            object : PrinterConnection {
                override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) { socket.outputStream.write(bytes); socket.outputStream.flush() }
                override fun close() { runCatching { socket.close() } }
            }
        } catch (error: Throwable) { runCatching { socket.close() }; throw error }
    }
    private companion object { val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") }
}
