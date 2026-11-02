package com.lanchoneteshirley.impressao.network

import com.lanchoneteshirley.impressao.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
            override fun onFailure(call: Call, error: IOException) { if (!call.isCanceled()) onFailure(error.message ?: "SSE indisponivel") }
            override fun onResponse(call: Call, response: Response) { response.use {
                if (!it.isSuccessful) { onFailure("SSE HTTP ${it.code}"); return }
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
            if (!response.isSuccessful) throw PrintApiException(response.code, errorMessage(responseBody))
            responseBody
        }
    }
    private fun errorMessage(body: String): String = runCatching { Json.parseToJsonElement(body).jsonObject["message"]?.toString()?.trim('"') }.getOrNull() ?: "Falha de comunicacao com a API."

    private companion object {
        fun defaultClient() = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    }
}

class PrintApiException(val status: Int, override val message: String) : IOException(message)
