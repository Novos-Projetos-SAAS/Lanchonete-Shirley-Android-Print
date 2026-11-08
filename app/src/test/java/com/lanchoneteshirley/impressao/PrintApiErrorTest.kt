package com.lanchoneteshirley.impressao

import com.lanchoneteshirley.impressao.network.ErrorContext
import com.lanchoneteshirley.impressao.network.decodePrintApiError
import com.lanchoneteshirley.impressao.network.operatorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PrintApiErrorTest {
    @Test fun `le envelope seguro sem expor corpo bruto`() {
        val error = decodePrintApiError(422, """{"status":"fail","code":"VALIDATION_ERROR","message":"Revise a configuração.","request_id":"req-1","details":[{"path":"codigo","message":"Código inválido."}]}""")
        assertEquals("VALIDATION_ERROR", error.code)
        assertEquals("Revise a configuração.", error.message)
        assertEquals("req-1", error.requestId)
        assertEquals("codigo", error.details.single().path)
    }

    @Test fun `usa mensagem por status para resposta invalida ou tecnica`() {
        val invalid = decodePrintApiError(503, "<html>proxy indisponível</html>")
        assertEquals("HTTP_503", invalid.code)
        assertEquals("O servidor está temporariamente indisponível. O app tentará novamente.", invalid.message)
        val technical = decodePrintApiError(500, """{"message":"IllegalStateException stack trace at secret(Token.kt:1)"}""")
        assertFalse(technical.message.contains("secret"))
        assertEquals("O pareamento não é mais válido. Desvincule o aparelho e faça um novo pareamento.", decodePrintApiError(401, "").message)
    }

    @Test fun `diferencia timeout rede e bluetooth`() {
        assertEquals("O servidor demorou para responder. Verifique a conexão e tente novamente.", operatorMessage(SocketTimeoutException(), "fallback"))
        assertEquals("Não foi possível conectar ao servidor. Verifique a internet e o endereço da API.", operatorMessage(UnknownHostException(), "fallback"))
        assertEquals("Não foi possível comunicar com a impressora Bluetooth. Verifique se ela está ligada, próxima e conectada.", operatorMessage(java.io.IOException("socket"), "fallback", ErrorContext.BLUETOOTH))
    }
}
