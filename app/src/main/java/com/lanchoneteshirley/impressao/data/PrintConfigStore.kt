package com.lanchoneteshirley.impressao.data

import android.content.Context
import java.util.UUID

class PrintConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences("print_config", Context.MODE_PRIVATE)
    fun load(): LocalPrintConfig {
        val installationId = preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("installation_id", it).apply() }
        return LocalPrintConfig(
            apiUrl = preferences.getString("api_url", "") ?: "", installationId = installationId,
            deviceName = preferences.getString("device_name", android.os.Build.MODEL) ?: android.os.Build.MODEL,
            printerName = preferences.getString("printer_name", "") ?: "", printerAddress = preferences.getString("printer_address", "") ?: "",
            paperWidth = preferences.getInt("paper_width", 58), encoding = preferences.getString("encoding", "CP860") ?: "CP860",
            autoCut = preferences.getBoolean("auto_cut", false), paired = preferences.getBoolean("paired", false),
        )
    }
    fun saveConnection(apiUrl: String, deviceName: String, paired: Boolean = true) {
        preferences.edit().putString("api_url", apiUrl.trimEnd('/')).putString("device_name", deviceName).putBoolean("paired", paired).apply()
    }
    fun savePrinter(name: String, address: String, paperWidth: Int, encoding: String, autoCut: Boolean) {
        preferences.edit().putString("printer_name", name).putString("printer_address", address).putInt("paper_width", paperWidth).putString("encoding", encoding).putBoolean("auto_cut", autoCut).apply()
    }
    fun setRuntime(shiftActive: Boolean, status: String, error: String? = null) {
        preferences.edit().putBoolean("shift_active", shiftActive).putString("runtime_status", status).putString("runtime_error", error).putLong("runtime_updated_at", System.currentTimeMillis()).commit()
    }
    fun setShiftRequested(requested: Boolean) { preferences.edit().putBoolean("shift_requested", requested).commit() }
    fun isShiftRequested() = preferences.getBoolean("shift_requested", false)
    fun isShiftActive() = preferences.getBoolean("shift_active", false)
    fun runtimeStatus() = if (hasCriticalWarning()) "ATENCAO" else preferences.getString("runtime_status", "PARADO") ?: "PARADO"
    fun runtimeError() = criticalWarning() ?: preferences.getString("runtime_error", null)

    fun savePendingReport(report: PendingReport) {
        preferences.edit().putString("pending_job_id", report.jobId).putString("pending_result", report.result).putString("pending_error", report.error)
            .putBoolean("pending_sending_started", report.sendingStarted).commit()
    }
    fun pendingReport(): PendingReport? {
        val jobId = preferences.getString("pending_job_id", null) ?: return null
        val result = preferences.getString("pending_result", null) ?: return null
        return PendingReport(jobId, result, preferences.getString("pending_error", null), preferences.getBoolean("pending_sending_started", false))
    }
    fun clearPendingReport() {
        preferences.edit().remove("pending_job_id").remove("pending_result")
            .remove("pending_error").remove("pending_sending_started").commit()
    }

    fun setCriticalWarning(jobId: String?, message: String, manualResolution: Boolean) {
        preferences.edit().putString("critical_job_id", jobId).putString("critical_warning", message)
            .putBoolean("critical_manual", manualResolution).commit()
    }
    fun hasCriticalWarning() = !preferences.getString("critical_warning", null).isNullOrBlank()
    fun criticalWarning() = preferences.getString("critical_warning", null)
    fun warningRequiresManualResolution() = preferences.getBoolean("critical_manual", false)
    fun clearCriticalWarningIfAutomatic() { if (!warningRequiresManualResolution()) clearCriticalWarning() }
    fun clearCriticalWarning() {
        preferences.edit().remove("critical_job_id").remove("critical_warning").remove("critical_manual").commit()
    }

    fun clearPairing() {
        preferences.edit().putBoolean("paired", false).putBoolean("shift_active", false).putBoolean("shift_requested", false)
            .remove("api_url").putString("runtime_status", "PARADO").remove("runtime_error").commit()
        clearPendingReport(); clearCriticalWarning()
    }
}
