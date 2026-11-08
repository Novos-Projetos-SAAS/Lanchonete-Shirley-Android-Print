package com.lanchoneteshirley.impressao.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class ApiEnvelope<T>(val status: String, val data: T)
@Serializable data class ApiErrorDetail(val path: String? = null, val message: String? = null)
@Serializable data class ApiErrorEnvelope(
    val status: String? = null,
    val code: String? = null,
    val message: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val details: List<ApiErrorDetail> = emptyList(),
)
@Serializable data class PairRequest(val codigo: String, @SerialName("installation_id") val installationId: String, val nome: String, @SerialName("app_version") val appVersion: String, @SerialName("android_version") val androidVersion: String)
@Serializable data class DeviceInfo(val id: String, val nome: String)
@Serializable data class PairResponse(val dispositivo: DeviceInfo, val credencial: String, @SerialName("credencial_expira_em") val credentialExpiresAt: String)
@Serializable data class HeartbeatRequest(
    @SerialName("turno_ativo") val shiftActive: Boolean, @SerialName("app_version") val appVersion: String,
    @SerialName("android_version") val androidVersion: String, @SerialName("impressora_nome") val printerName: String?,
    @SerialName("impressora_endereco") val printerAddress: String?, @SerialName("bobina_mm") val paperWidth: Int,
    val codificacao: String, @SerialName("corte_automatico") val autoCut: Boolean, val status: String, val erro: String? = null,
)
@Serializable data class PrinterSettings(val endereco: String? = null, val nome: String? = null, @SerialName("bobina_mm") val paperWidth: Int = 58, val codificacao: String = "CP860", @SerialName("corte_automatico") val autoCut: Boolean = false)
@Serializable data class PrintJob(
    val id: String, @SerialName("pedido_id") val orderId: String? = null, val tipo: String, val tentativa: Int,
    @SerialName("lease_token") val leaseToken: String, @SerialName("lease_ate") val leaseUntil: String,
    @SerialName("conteudo_texto") val text: String, val impressora: PrinterSettings,
)
@Serializable data class LeaseRequest(@SerialName("lease_token") val leaseToken: String)
@Serializable data class ResultRequest(@SerialName("lease_token") val leaseToken: String, val resultado: String, val erro: String? = null)
@Serializable data class JobState(
    val id: String, val status: String, val resultado: String? = null,
    @SerialName("envio_iniciado_em") val sendingStartedAt: String? = null,
    @SerialName("enviado_em") val sentAt: String? = null,
    @SerialName("concluido_em") val completedAt: String? = null,
)

data class PendingReport(
    val jobId: String, val result: String, val error: String?, val sendingStarted: Boolean,
)

data class LocalPrintConfig(
    val apiUrl: String, val installationId: String, val deviceName: String, val printerName: String,
    val printerAddress: String, val paperWidth: Int, val encoding: String, val autoCut: Boolean, val paired: Boolean,
)
