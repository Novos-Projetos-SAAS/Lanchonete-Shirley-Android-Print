package com.lanchoneteshirley.impressao

import com.lanchoneteshirley.impressao.data.JobState
import com.lanchoneteshirley.impressao.data.PendingReport
import com.lanchoneteshirley.impressao.service.JobReconciliation
import com.lanchoneteshirley.impressao.service.ReconciliationDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class JobReconciliationTest {
    private fun pending(started: Boolean, result: String = if (started) "RESULTADO_INDETERMINADO" else "FALHA_ANTES_ENVIO") =
        PendingReport("10", result, "rede indisponivel", started)

    @Test fun confirmsLostHttpResponseWithoutPrintingAgain() {
        assertEquals(ReconciliationDecision.CONFIRMED, JobReconciliation.decide(pending(true, "ENVIADO"), JobState("10", "CONCLUIDA", "ENVIADO")))
    }

    @Test fun requiresManualReviewAfterSendingBoundary() {
        assertEquals(ReconciliationDecision.MANUAL_REVIEW, JobReconciliation.decide(pending(true), JobState("10", "RESULTADO_INDETERMINADO", "RESULTADO_INDETERMINADO")))
        assertEquals(ReconciliationDecision.WAIT, JobReconciliation.decide(pending(true), JobState("10", "PENDENTE", null)))
    }

    @Test fun allowsServerRetryOnlyWhenNoBytesWereSent() {
        assertEquals(ReconciliationDecision.SAFE_TO_CONTINUE, JobReconciliation.decide(pending(false), JobState("10", "PENDENTE", null)))
    }
}
