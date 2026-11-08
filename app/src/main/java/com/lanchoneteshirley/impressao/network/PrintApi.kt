package com.lanchoneteshirley.impressao.network

import com.lanchoneteshirley.impressao.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class PrintApi(private val baseUrl: String, private val client: OkHttpClient = defaultClient()) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    init { require(baseUrl.startsWith("https://")) { "A API deve usar HTTPS." } }

    suspend fun pair(request: PairRequest): PairResponse = post("/agente-impressao/android/parear", json.encodeToString(request))!!.let { json.decodeFromString<ApiEnvelope<PairResponse>>(it).data }
    suspend fun rotate(token: String): PairResponse = post("/agente-impressao/android/credencial/rotacionar", "{}", token)!!.let { json.decodeFromString<ApiEnvelope<PairResponse>>(it).data }
    suspend fun unlink(token: String) { post("/agente-impressao/android/credencial/desvincular", "{}", token) }
    suspend fun heartbeat(token: String, request: HeartbeatRequest) { post("/agente-impressao/android/heartbeat", json.encodeToString(request), token) }
    suspend fun reserve(token: String): PrintJob? = post("/agente-impressao/android/jobs/reservar", "{}", token, true)?.let { json.decodeFromString<ApiEnvelope<PrintJob>>(it).data }
    suspend fun startSending(token: String, job: PrintJob) { post("/agente-impressao/android/jobs/${job.id}/iniciar-envio", json.encodeToString(LeaseRequest(job.leaseToken)), token) }
    suspend fun result(token: String, job: PrintJob, result: String, error: String? = null) = result(token, job.id, job.leaseToken, result, error)
    suspend fun result(token: String, jobId: String, leaseToken: String, result: String, error: String? = null) { post("/agente-impressao/android/jobs/$jobId/resultado", json.encodeToString(ResultRequest(leaseToken, result, error?.take(500))), token) }
    suspend fun jobState(token: String, jobId: String): JobState = get("/agente-impressao/android/jobs/$jobId/estado", token).let { json.decodeFromString<ApiEnvelope<JobState>>(it).data }

    fun events(token: String, onSignal: () -> Unit, onFailure: (String) -> Unit): Call {
        val request = Request.Builder().url(url("/agente-impressao/android/eventos")).header("Authorization", "Bearer $token").build()
        return client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build().newCall(request).also { call -> call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (!call.isCanceled()) onFailure(operatorMessage(error, "A conexão de eventos está indisponível. A busca periódica continuará ativa.")) }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { onFailure(statusMessage(it.code)); return }
                val source = it.body?.source() ?: return
                while (!source.exhausted() && !call.isCanceled()) if (source.readUtf8Line()?.startsWith("event: impressao_disponivel") == true) onSignal()
            }; if (!call.isCanceled()) onFailure("SSE encerrado") }
        }) }
    }

    private fun url(path: String) = "${baseUrl.trimEnd('/')}$path"
    private suspend fun get(path: String, token: String): String = execute(Request.Builder().url(url(path)).get().header("Accept", "application/json").header("Authorization", "Bearer $token").build()) ?: ""
    private suspend fun post(path: String, body: String, token: String? = null, allowEmpty: Boolean = false): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url(path)).post(body.toRequestBody(mediaType)).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        execute(builder.build(), allowEmpty)
    }
    private suspend fun execute(request: Request, allowEmpty: Boolean = false): String? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        client.newCall(request).execute().use { response ->
            if (allowEmpty && response.code == 204) return@withContext null
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw decodePrintApiError(response.code, responseBody, json)
            responseBody
        }
    }

    private companion object {
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
}

class PrintApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val requestId: String? = null,
    val details: List<ApiErrorDetail> = emptyList(),
) : IOException(message)

internal fun statusMessage(status: Int) = when (status) {
    400 -> "A solicitação não pôde ser processada. Revise os dados."
    401 -> "O pareamento não é mais válido. Desvincule o aparelho e faça um novo pareamento."
    403 -> "Este aparelho não tem permissão para realizar a operação."
    404 -> "O recurso solicitado não está disponível."
    409 -> "A fila mudou no servidor. Aguarde a reconciliação antes de tentar novamente."
    413 -> "O conteúdo excede o limite aceito pelo servidor."
    422 -> "Os dados enviados foram recusados. Revise a configuração."
    429 -> "Muitas tentativas. Aguarde um momento; o app tentará novamente."
    in 500..599 -> "O servidor está temporariamente indisponível. O app tentará novamente."
    else -> "Não foi possível concluir a comunicação com o servidor."
}

internal fun decodePrintApiError(status: Int, body: String, json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }): PrintApiException {
    val envelope = runCatching { json.decodeFromString<ApiErrorEnvelope>(body.take(64_000)) }.getOrNull()
    val serverMessage = envelope?.message?.trim()?.takeIf {
        it.isNotEmpty() && it.length <= 500 && !it.contains(Regex("exception|stack\s*trace|\\bat\\s+\\S+\\(", RegexOption.IGNORE_CASE))
    }
    val safeCode = envelope?.code?.takeIf { it.matches(Regex("[A-Z0-9_]{2,100}")) } ?: "HTTP_$status"
    val safeRequestId = envelope?.requestId?.takeIf { it.length <= 100 }
    val safeDetails = envelope?.details.orEmpty().mapNotNull { detail ->
        val path = detail.path?.takeIf { it.length <= 200 }
        val message = detail.message?.takeIf { it.isNotBlank() && it.length <= 500 }
        if (path == null && message == null) null else ApiErrorDetail(path, message)
    }
    return PrintApiException(status, safeCode, serverMessage ?: statusMessage(status), safeRequestId, safeDetails)
}

enum class ErrorContext { API, PAIRING, BLUETOOTH }

fun operatorMessage(error: Throwable, fallback: String, context: ErrorContext = ErrorContext.API): String = when (error) {
    is PrintApiException -> error.message
    is SocketTimeoutException -> "O servidor demorou para responder. Verifique a conexão e tente novamente."
    is UnknownHostException, is ConnectException -> "Não foi possível conectar ao servidor. Verifique a internet e o endereço da API."
    is SecurityException -> if (context == ErrorContext.BLUETOOTH) "Autorize o acesso a dispositivos próximos para usar a impressora Bluetooth." else fallback
    is IOException -> if (context == ErrorContext.BLUETOOTH) "Não foi possível comunicar com a impressora Bluetooth. Verifique se ela está ligada, próxima e conectada." else "A conexão com o servidor foi interrompida. O app tentará novamente."
    is IllegalArgumentException, is IllegalStateException -> error.message?.take(300)?.takeIf { it.isNotBlank() } ?: fallback
    else -> fallback
}
