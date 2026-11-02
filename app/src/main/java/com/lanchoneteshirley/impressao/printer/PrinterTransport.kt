package com.lanchoneteshirley.impressao.printer

interface PrinterTransport { suspend fun connect(address: String): PrinterConnection }
interface PrinterConnection : AutoCloseable { suspend fun send(bytes: ByteArray) }
