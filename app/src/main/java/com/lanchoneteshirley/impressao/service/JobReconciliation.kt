package com.lanchoneteshirley.impressao.service

import com.lanchoneteshirley.impressao.data.JobState
import com.lanchoneteshirley.impressao.data.PendingReport

enum class ReconciliationDecision { CONFIRMED, SAFE_TO_CONTINUE, MANUAL_REVIEW, WAIT }

object JobReconciliation {
    fun decide(pending: PendingReport, remote: JobState): ReconciliationDecision = when {
        remote.status == "CONCLUIDA" && remote.resultado == "ENVIADO" -> ReconciliationDecision.CONFIRMED
        remote.status == "RESULTADO_INDETERMINADO" || remote.resultado == "RESULTADO_INDETERMINADO" -> ReconciliationDecision.MANUAL_REVIEW
        !pending.sendingStarted && remote.status in setOf("PENDENTE", "FALHOU") -> ReconciliationDecision.SAFE_TO_CONTINUE
        else -> ReconciliationDecision.WAIT
    }
}
