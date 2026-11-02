package com.lanchoneteshirley.impressao.printer

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPosEncoder {
    fun encode(text: String, encoding: String, autoCut: Boolean): ByteArray = ByteArrayOutputStream().use { output ->
        output.write(byteArrayOf(0x1B, 0x40)); output.write(byteArrayOf(0x1B, 0x74, (if (encoding == "CP850") 2 else 3).toByte()))
        output.write(text.toByteArray(Charset.forName(encoding))); output.write(byteArrayOf(0x0A, 0x0A, 0x0A))
        if (autoCut) output.write(byteArrayOf(0x1D, 0x56, 0x41, 0x03)); output.toByteArray()
    }
}
