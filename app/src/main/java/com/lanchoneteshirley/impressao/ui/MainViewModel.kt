package com.lanchoneteshirley.impressao.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanchoneteshirley.impressao.BuildConfig
import com.lanchoneteshirley.impressao.data.PairRequest
import com.lanchoneteshirley.impressao.data.PrintConfigStore
import com.lanchoneteshirley.impressao.network.PrintApi
import com.lanchoneteshirley.impressao.network.PrintApiException
import com.lanchoneteshirley.impressao.printer.BluetoothSppPrinterTransport
import com.lanchoneteshirley.impressao.printer.EscPosEncoder
import com.lanchoneteshirley.impressao.security.SecureTokenStore
import com.lanchoneteshirley.impressao.service.PrintForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class PrintUiState(
    val apiUrl: String = "", val code: String = "", val deviceName: String = Build.MODEL,
    val paired: Boolean = false, val printerName: String = "", val printerAddress: String = "",
    val paperWidth: Int = 58, val encoding: String = "CP860", val autoCut: Boolean = false,
    val shiftActive: Boolean = false, val runtimeStatus: String = "PARADO", val message: String? = null,
    val warningCanBeAcknowledged: Boolean = false, val busy: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val configStore = PrintConfigStore(application)
    private val tokenStore = SecureTokenStore(application)
    var state by mutableStateOf(loadState()); private set

    private fun loadState(): PrintUiState {
        val config = configStore.load()
        if (configStore.isShiftActive() && !PrintForegroundService.isRunning()) {
            configStore.setShiftRequested(false)
            configStore.setRuntime(false, "INTERROMPIDO", configStore.runtimeError() ?: "O serviço não está ativo. Inicie o turno manualmente.")
        }
        return PrintUiState(config.apiUrl, deviceName = config.deviceName, paired = config.paired && tokenStore.load() != null,
            printerName = config.printerName, printerAddress = config.printerAddress, paperWidth = config.paperWidth,
            encoding = config.encoding, autoCut = config.autoCut, shiftActive = PrintForegroundService.isRunning(),
            runtimeStatus = configStore.runtimeStatus(), message = configStore.runtimeError(),
            warningCanBeAcknowledged = configStore.warningRequiresManualResolution() && configStore.pendingReport() == null)
    }
    fun update(transform: (PrintUiState) -> PrintUiState) { state = transform(state) }
    fun prefill(apiUrl: String?, code: String?) { state = state.copy(apiUrl = apiUrl ?: state.apiUrl, code = code ?: state.code) }
    fun refreshRuntime() {
        val running = PrintForegroundService.isRunning()
        if (!running && configStore.isShiftActive()) {
            configStore.setShiftRequested(false)
            configStore.setRuntime(false, "INTERROMPIDO", configStore.runtimeError() ?: "O serviço foi encerrado. Inicie o turno manualmente.")
        }
        state = state.copy(shiftActive = running, runtimeStatus = configStore.runtimeStatus(), message = configStore.runtimeError(),
            warningCanBeAcknowledged = configStore.warningRequiresManualResolution() && configStore.pendingReport() == null)
    }

    fun pair() = viewModelScope.launch {
        state = state.copy(busy = true, message = null)
        try {
            require(state.apiUrl.startsWith("https://")) { "Informe uma URL HTTPS da API." }
            val config = configStore.load()
            val response = PrintApi(state.apiUrl.trimEnd('/')).pair(PairRequest(state.code.trim().uppercase(), config.installationId, state.deviceName.trim(), BuildConfig.VERSION_NAME, Build.VERSION.RELEASE))
            tokenStore.save(response.credencial); configStore.saveConnection(state.apiUrl, state.deviceName)
            state = state.copy(paired = true, busy = false, code = "", message = "Aparelho pareado com segurança.")
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { state = state.copy(busy = false, message = error.message ?: "Não foi possível parear.") }
    }

    fun selectPrinter(name: String, address: String) { state = state.copy(printerName = name, printerAddress = address); persistPrinter() }
    fun setPaperWidth(value: Int) { state = state.copy(paperWidth = value); persistPrinter() }
    fun setEncoding(value: String) { state = state.copy(encoding = value); persistPrinter() }
    fun setAutoCut(value: Boolean) { state = state.copy(autoCut = value); persistPrinter() }
    private fun persistPrinter() { configStore.savePrinter(state.printerName, state.printerAddress, state.paperWidth, state.encoding, state.autoCut) }
    fun startShift() { persistPrinter(); PrintForegroundService.start(getApplication()); state = state.copy(shiftActive = false, runtimeStatus = "INICIANDO", message = null) }
    fun stopShift() { PrintForegroundService.stop(getApplication()); state = state.copy(shiftActive = false, runtimeStatus = "PARANDO") }
    fun rotateCredential() = viewModelScope.launch {
        state = state.copy(busy = true, message = null)
        try {
            check(!state.shiftActive) { "Encerre o turno antes de rotacionar a credencial." }
            val token = tokenStore.load() ?: error("Credencial local ausente. Faça novo pareamento.")
            val response = PrintApi(configStore.load().apiUrl).rotate(token)
            tokenStore.save(response.credencial)
            state = state.copy(busy = false, message = "Credencial rotacionada com sucesso.")
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { state = state.copy(busy = false, message = error.message ?: "Não foi possível rotacionar a credencial.") }
    }

    fun unlink() = viewModelScope.launch {
        state = state.copy(busy = true, message = null)
        try {
            check(!PrintForegroundService.isRunning()) { "Encerre o turno antes de desvincular o aparelho." }
            val config = configStore.load()
            val token = tokenStore.load()
            if (!token.isNullOrBlank() && config.apiUrl.isNotBlank()) {
                try { PrintApi(config.apiUrl).unlink(token) }
                catch (error: PrintApiException) { if (error.status !in setOf(401, 404)) throw error }
            }
            tokenStore.clear(); configStore.clearPairing()
            state = loadState().copy(message = "Aparelho desvinculado. Gere um novo código no painel para parear novamente.")
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { state = state.copy(busy = false, message = error.message ?: "Não foi possível desvincular com segurança.") }
    }

    fun testPrinter() = viewModelScope.launch {
        state = state.copy(busy = true, message = null)
        try {
            check(!PrintForegroundService.isRunning()) { "Encerre o turno antes do teste local." }
            check(state.printerAddress.isNotBlank()) { "Selecione uma impressora Bluetooth." }
            persistPrinter()
            val text = "LANCHONETE SHIRLEY\nTESTE LOCAL DE IMPRESSAO\nBobina ${state.paperWidth} mm · ${state.encoding}\nABCDEFGHIJKLMNOPQRSTUVWXYZ\n0123456789\n\nConfirme visualmente o papel.\n"
            BluetoothSppPrinterTransport(getApplication()).connect(state.printerAddress).use { connection ->
                connection.send(EscPosEncoder.encode(text, state.encoding, state.autoCut))
            }
            state = state.copy(busy = false, message = "Bytes do teste enviados. Confirme visualmente se o papel saiu corretamente.")
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { state = state.copy(busy = false, message = error.message ?: "Não foi possível enviar o teste à impressora.") }
    }

    fun acknowledgeWarning() {
        if (configStore.pendingReport() != null) return
        configStore.clearCriticalWarning()
        refreshRuntime()
        state = state.copy(message = "Aviso marcado como conferido pelo operador.")
    }
}
