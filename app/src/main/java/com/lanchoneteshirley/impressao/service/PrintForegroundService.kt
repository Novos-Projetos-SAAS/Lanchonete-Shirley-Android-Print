package com.lanchoneteshirley.impressao.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.lanchoneteshirley.impressao.BuildConfig
import com.lanchoneteshirley.impressao.MainActivity
import com.lanchoneteshirley.impressao.data.HeartbeatRequest
import com.lanchoneteshirley.impressao.data.PendingReport
import com.lanchoneteshirley.impressao.data.PrintConfigStore
import com.lanchoneteshirley.impressao.data.PrintJob
import com.lanchoneteshirley.impressao.network.PrintApi
import com.lanchoneteshirley.impressao.network.PrintApiException
import com.lanchoneteshirley.impressao.printer.BluetoothSppPrinterTransport
import com.lanchoneteshirley.impressao.printer.EscPosEncoder
import com.lanchoneteshirley.impressao.security.SecureTokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call

class PrintForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private lateinit var configStore: PrintConfigStore
    private lateinit var tokenStore: SecureTokenStore
    @Volatile private var signalCall: Call? = null
    private var shiftJob: Job? = null

    override fun onCreate() {
        super.onCreate(); configStore = PrintConfigStore(this); tokenStore = SecureTokenStore(this); createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { configStore.setShiftRequested(false); updateNotification("Encerrando turno após o trabalho atual…"); scope.launch { stopShift() }; return START_NOT_STICKY }
        startForeground("Conectando ao balcão…")
        running = true
        configStore.setShiftRequested(true)
        configStore.setRuntime(true, "CONECTANDO")
        if (shiftJob?.isActive != true) shiftJob = scope.launch { runShift() }
        return START_NOT_STICKY
    }

    private suspend fun runShift() {
        val config = configStore.load(); val token = tokenStore.load()
        if (!config.paired || token.isNullOrBlank() || config.apiUrl.isBlank() || config.printerAddress.isBlank()) {
            failAndStop("Pareie o aparelho e escolha uma impressora antes de iniciar."); return
        }
        val api = runCatching { PrintApi(config.apiUrl) }.getOrElse { failAndStop(it.message ?: "API invalida"); return }
        updateNotification("Turno ativo · conectando")
        var failures = 0
        while (scope.isActive && configStore.isShiftRequested()) {
            var nextDelay = RECONCILIATION_MS
            try {
                val queueSafe = reconcilePendingReport(api, token)
                val heartbeatStatus = configStore.runtimeStatus().takeIf { it == "ATENCAO" } ?: "PRONTO"
                heartbeat(api, token, true, heartbeatStatus, configStore.runtimeError())
                if (signalCall == null) signalCall = api.events(token, { scope.launch { processQueue(api, token) } }, { message -> configStore.setRuntime(true, "RECONECTANDO", message); signalCall = null })
                configStore.setRuntime(true, "PRONTO"); updateNotification("Turno ativo · aguardando pedidos")
                if (queueSafe) processQueue(api, token)
                failures = 0
            } catch (error: CancellationException) {
                throw error
            } catch (error: PrintApiException) {
                if (error.status == 401) { failAndStop("Credencial revogada ou expirada. Desvincule este aparelho e faça um novo pareamento."); return }
                failures += 1
                configStore.setRuntime(true, "ERRO", safeError(error)); updateNotification("Turno ativo · conexão instável")
                nextDelay = (RECONCILIATION_MS * (1L shl failures.coerceAtMost(2))).coerceAtMost(MAX_BACKOFF_MS)
            } catch (error: Throwable) {
                failures += 1
                configStore.setRuntime(true, "ERRO", safeError(error)); updateNotification("Turno ativo · conexão instável")
                nextDelay = (RECONCILIATION_MS * (1L shl failures.coerceAtMost(2))).coerceAtMost(MAX_BACKOFF_MS)
            }
            delay(nextDelay)
        }
    }

    private suspend fun processQueue(api: PrintApi, token: String) = queueMutex.withLock {
        if (configStore.pendingReport() != null) return@withLock
        while (scope.isActive && configStore.isShiftRequested()) {
            val job = api.reserve(token) ?: return
            if (!printJob(api, token, job)) return
        }
    }

    private suspend fun printJob(api: PrintApi, token: String, job: PrintJob): Boolean {
        val config = configStore.load(); updateNotification("Imprimindo ${job.orderId?.let { "pedido #$it" } ?: "teste"}")
        val bytes = EscPosEncoder.encode(job.text, job.impressora.codificacao, job.impressora.autoCut)
        val connection = try { BluetoothSppPrinterTransport(this).connect(config.printerAddress) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { return reportSafely(api, token, job, "FALHA_ANTES_ENVIO", safeError(error), false) }
        connection.use {
            var sendingStarted = false
            try {
                api.startSending(token, job)
                sendingStarted = true
                it.send(bytes)
                return reportSafely(api, token, job, "ENVIADO", null, true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val result = if (sendingStarted) "RESULTADO_INDETERMINADO" else "FALHA_ANTES_ENVIO"
                val reported = reportSafely(api, token, job, result, safeError(error), sendingStarted)
                if (sendingStarted) configStore.setCriticalWarning(job.id, "Resultado indeterminado no job ${job.id}; confira a impressora antes de emitir 2ª via.", true)
                return reported && !sendingStarted
            }
        }
    }

    private suspend fun reportSafely(api: PrintApi, token: String, job: PrintJob, result: String, error: String?, sendingStarted: Boolean): Boolean {
        repeat(3) { attempt ->
            try { api.result(token, job, result, error); return true }
            catch (failure: CancellationException) { throw failure }
            catch (_: Throwable) { if (attempt < 2) delay(750L * (attempt + 1)) }
        }
        val pending = PendingReport(job.id, result, error, sendingStarted)
        tokenStore.savePendingLease(job.id, job.leaseToken)
        configStore.savePendingReport(pending)
        configStore.setCriticalWarning(job.id, "A confirmação do job ${job.id} ainda não chegou ao servidor. A fila foi pausada para evitar duplicidade.", sendingStarted)
        updateNotification("Atenção · confirmação pendente")
        return false
    }

    private suspend fun reconcilePendingReport(api: PrintApi, token: String): Boolean {
        val pending = configStore.pendingReport() ?: return true
        val pendingLease = tokenStore.loadPendingLease(pending.jobId)
        if (pendingLease != null) {
            try {
                api.result(token, pending.jobId, pendingLease, pending.result, pending.error)
                clearPendingReport()
                if (pending.result == "RESULTADO_INDETERMINADO") {
                    configStore.setCriticalWarning(pending.jobId, "Resultado indeterminado no job ${pending.jobId}; confira a impressora antes de emitir 2ª via.", true)
                } else configStore.clearCriticalWarning()
                return true
            } catch (error: CancellationException) {
                throw error
            } catch (error: PrintApiException) {
                if (error.status == 401) throw error
            } catch (_: Throwable) { }
        }

        val remote = try { api.jobState(token, pending.jobId) }
        catch (error: CancellationException) { throw error }
        catch (error: PrintApiException) { if (error.status == 401) throw error else null }
        catch (_: Throwable) { null }

        return when (remote?.let { JobReconciliation.decide(pending, it) }) {
            ReconciliationDecision.CONFIRMED -> { clearPendingReport(); configStore.clearCriticalWarning(); true }
            ReconciliationDecision.SAFE_TO_CONTINUE -> { clearPendingReport(); configStore.clearCriticalWarningIfAutomatic(); true }
            ReconciliationDecision.MANUAL_REVIEW -> {
                clearPendingReport()
                configStore.setCriticalWarning(pending.jobId, "Resultado indeterminado no job ${pending.jobId}; confira a impressora antes de emitir 2ª via.", true)
                true
            }
            else -> false
        }
    }

    private fun clearPendingReport() { configStore.clearPendingReport(); tokenStore.clearPendingLease() }

    private suspend fun heartbeat(api: PrintApi, token: String, active: Boolean, status: String, error: String? = null) {
        val config = configStore.load()
        api.heartbeat(token, HeartbeatRequest(active, BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, config.printerName.ifBlank { null }, config.printerAddress.ifBlank { null }, config.paperWidth, config.encoding, config.autoCut, status, error))
    }

    private suspend fun stopShift() = queueMutex.withLock {
        val config = configStore.load(); val token = tokenStore.load()
        if (token != null && config.apiUrl.isNotBlank()) runCatching { heartbeat(PrintApi(config.apiUrl), token, false, "DESCONECTADO") }
        configStore.setShiftRequested(false); configStore.setRuntime(false, "PARADO"); signalCall?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun failAndStop(message: String) { configStore.setShiftRequested(false); configStore.setRuntime(false, "ERRO", message); updateNotification(message); signalCall?.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    private fun safeError(error: Throwable) = (error.message ?: error.javaClass.simpleName).replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]").take(500)

    private fun startForeground(content: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(content), type)
    }
    private fun updateNotification(content: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(content)) }
    private fun notification(content: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, PrintForegroundService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Shirley Impressão")
            .setContentText(content).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).addAction(0, "Encerrar turno", stop).build()
    }
    private fun createChannel() { getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Turno de impressão", NotificationManager.IMPORTANCE_LOW)) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        val wasActive = configStore.isShiftActive()
        running = false
        signalCall?.cancel(); scope.cancel()
        if (wasActive) {
            configStore.setShiftRequested(false)
            val interruptedMessage = if (configStore.hasCriticalWarning()) null else configStore.runtimeError() ?: "O serviço foi encerrado. Abra o app e inicie o turno manualmente."
            configStore.setRuntime(false, "INTERROMPIDO", interruptedMessage)
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.lanchoneteshirley.impressao.START"
        const val ACTION_STOP = "com.lanchoneteshirley.impressao.STOP"
        private const val CHANNEL_ID = "shirley_print_shift"
        private const val NOTIFICATION_ID = 901
        private const val RECONCILIATION_MS = 15_000L
        private const val MAX_BACKOFF_MS = 60_000L
        @Volatile private var running = false
        fun isRunning() = running
        fun start(context: Context) {
            PrintConfigStore(context).apply { setShiftRequested(true); setRuntime(false, "INICIANDO") }
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, PrintForegroundService::class.java).setAction(ACTION_START))
        }
        fun stop(context: Context) {
            PrintConfigStore(context).setShiftRequested(false)
            if (running) context.startService(Intent(context, PrintForegroundService::class.java).setAction(ACTION_STOP))
            else PrintConfigStore(context).setRuntime(false, "PARADO")
        }
    }
}
