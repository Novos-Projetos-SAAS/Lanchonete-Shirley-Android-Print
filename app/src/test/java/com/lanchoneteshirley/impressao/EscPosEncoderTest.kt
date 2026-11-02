package com.lanchoneteshirley.impressao

import com.lanchoneteshirley.impressao.printer.EscPosEncoder
import org.junit.Assert.*
import org.junit.Test

class EscPosEncoderTest {
    @Test fun initializesPrinterAndKeepsPortugueseTextInCp860() {
        val bytes = EscPosEncoder.encode("Porções · 2ª VIA\n", "CP860", false)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), bytes.copyOfRange(0, 2))
        assertFalse(bytes.takeLast(4).toByteArray().contentEquals(byteArrayOf(0x1D, 0x56, 0x41, 0x03)))
        assertTrue(bytes.size > 10)
    }

    @Test fun appendsPartialCutOnlyWhenEnabled() {
        val bytes = EscPosEncoder.encode("Teste", "CP850", true)
        assertArrayEquals(byteArrayOf(0x1D, 0x56, 0x41, 0x03), bytes.takeLast(4).toByteArray())
    }
}
