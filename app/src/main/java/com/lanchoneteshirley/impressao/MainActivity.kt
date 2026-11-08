package com.lanchoneteshirley.impressao

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lanchoneteshirley.impressao.ui.MainViewModel
import com.lanchoneteshirley.impressao.ui.ShirleyTheme
import kotlinx.coroutines.delay

data class BondedPrinter(val name: String, val address: String)

class MainActivity : ComponentActivity() {
    private val resumeTick = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deepLinkApi = intent?.data?.getQueryParameter("api")
        val deepLinkCode = intent?.data?.getQueryParameter("codigo")
        setContent { ShirleyTheme { PrintApp(deepLinkApi, deepLinkCode, resumeTick.intValue) } }
    }
    override fun onResume() { super.onResume(); resumeTick.intValue += 1 }
}

@SuppressLint("MissingPermission")
private fun bondedPrinters(context: Context, granted: Boolean): List<BondedPrinter> {
    if (!granted) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return adapter.bondedDevices.map { BondedPrinter(it.name ?: "Impressora sem nome", it.address) }.sortedBy { it.name.lowercase() }
}

@SuppressLint("MissingPermission")
private fun bluetoothEnabled(context: Context, granted: Boolean): Boolean = granted && context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrintApp(deepLinkApi: String?, deepLinkCode: String?, resumeTick: Int, model: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    var printerRefreshTick by remember { mutableIntStateOf(0) }
    val bluetoothGranted = Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionTick++ }
    val bluetoothOn = remember(permissionTick, bluetoothGranted, resumeTick, printerRefreshTick) { bluetoothEnabled(context, bluetoothGranted) }
    val printers = remember(permissionTick, bluetoothGranted, resumeTick, printerRefreshTick) { bondedPrinters(context, bluetoothGranted) }
    val state = model.state

    LaunchedEffect(deepLinkApi, deepLinkCode) { model.prefill(deepLinkApi, deepLinkCode) }
    LaunchedEffect(Unit) { while (true) { model.refreshRuntime(); delay(2_000) } }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        CenterAlignedTopAppBar(title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("Shirley Impressão", fontWeight = FontWeight.Bold); Text("Terminal do balcão", style = MaterialTheme.typography.labelSmall) } })
    }) { insets ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Box(Modifier.widthIn(max = 760.dp)) { StatusStrip(state.runtimeStatus, state.shiftActive, state.message, state.warningCanBeAcknowledged, model::acknowledgeWarning) } }
            if (!state.paired) item { Box(Modifier.widthIn(max = 760.dp)) { PairingCard(model) } }
            else {
                item { Box(Modifier.widthIn(max = 760.dp)) { PrinterCard(model, printers, bluetoothGranted, bluetoothOn, { permissionLauncher.launch(requiredPermissions) }, { printerRefreshTick += 1 }, { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) } }
                item { Box(Modifier.widthIn(max = 760.dp)) { ShiftCard(model, bluetoothGranted, bluetoothOn) } }
            }
            item { Text("A fila do servidor é a fonte de verdade. O aviso em tempo real apenas acelera a consulta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 760.dp).padding(bottom = 24.dp)) }
        }
    }
}

@Composable private fun StatusStrip(status: String, active: Boolean, message: String?, warningCanBeAcknowledged: Boolean, acknowledgeWarning: () -> Unit) {
    val attention = status in listOf("ERRO", "ATENCAO")
    Card(colors = CardDefaults.cardColors(containerColor = when { attention -> MaterialTheme.colorScheme.errorContainer; active -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceVariant }), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (attention) Icons.Outlined.ErrorOutline else if (active) Icons.Outlined.CheckCircle else Icons.Outlined.PauseCircle, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (active) "Turno ativo · $status" else "Turno encerrado · $status", fontWeight = FontWeight.Bold)
                if (!message.isNullOrBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                if (warningCanBeAcknowledged) OutlinedButton(onClick = acknowledgeWarning, modifier = Modifier.heightIn(min = 48.dp)) { Text("Já conferi a impressora") }
            }
        }
    }
}

@Composable private fun PairingCard(model: MainViewModel) {
    val state = model.state
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Link, null); Text("Parear este aparelho", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
        Text("No painel web, gere um código temporário em Impressões. A credencial definitiva fica protegida pelo Android Keystore.")
        OutlinedTextField(value = state.apiUrl, onValueChange = { value -> model.update { it.copy(apiUrl = value) } }, label = { Text("URL HTTPS da API") }, placeholder = { Text("https://api.exemplo.com/api/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.code, onValueChange = { value -> model.update { it.copy(code = value.uppercase().take(8)) } }, label = { Text("Código de 8 caracteres") }, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.deviceName, onValueChange = { value -> model.update { it.copy(deviceName = value.take(120)) } }, label = { Text("Nome deste aparelho") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { model.pair() }, enabled = !state.busy && state.apiUrl.isNotBlank() && state.code.length == 8 && state.deviceName.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Link, null); Spacer(Modifier.width(8.dp)); Text(if (state.busy) "Pareando…" else "Parear com segurança") }
    } }
}

@Composable private fun PrinterCard(model: MainViewModel, printers: List<BondedPrinter>, granted: Boolean, bluetoothOn: Boolean, requestPermissions: () -> Unit, refreshPrinters: () -> Unit, openBluetoothSettings: () -> Unit) {
    val state = model.state
    var confirmTest by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Print, null); Text("Impressora térmica", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
        if (!granted) OutlinedButton(onClick = requestPermissions, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Bluetooth, null); Spacer(Modifier.width(8.dp)); Text("Permitir acesso ao Bluetooth") }
        else if (!bluetoothOn) {
            Text("O Bluetooth está desligado. Ative-o para localizar a impressora já pareada.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = openBluetoothSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.SettingsBluetooth, null); Spacer(Modifier.width(8.dp)); Text("Abrir configurações Bluetooth") }
        }
        else if (printers.isEmpty()) Text("Nenhum dispositivo pareado no Android. Pareie a impressora nas configurações do sistema e volte aqui.")
        else printers.forEach { printer ->
            val selected = printer.address == state.printerAddress
            OutlinedCard(onClick = { model.selectPrinter(printer.name, printer.address) }, colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected, onClick = null); Column { Text(printer.name, fontWeight = FontWeight.SemiBold); Text(printer.address, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        if (granted) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = refreshPrinters, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Atualizar") }
            OutlinedButton(onClick = openBluetoothSettings, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Outlined.SettingsBluetooth, null); Spacer(Modifier.width(8.dp)); Text("Parear") }
        }
        Text("Bobina", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(58, 80).forEach { width -> FilterChip(selected = state.paperWidth == width, onClick = { model.setPaperWidth(width) }, label = { Text("$width mm") }) } }
        Text("Tabela de caracteres", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("CP860", "CP850").forEach { encoding -> FilterChip(selected = state.encoding == encoding, onClick = { model.setEncoding(encoding) }, label = { Text(encoding) }) } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Corte automático", fontWeight = FontWeight.SemiBold); Text("Ative apenas se a impressora possuir guilhotina compatível.", style = MaterialTheme.typography.bodySmall) }; Switch(checked = state.autoCut, onCheckedChange = model::setAutoCut) }
        HorizontalDivider()
        OutlinedButton(onClick = { confirmTest = true }, enabled = bluetoothOn && state.printerAddress.isNotBlank() && !state.shiftActive && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Print, null); Spacer(Modifier.width(8.dp)); Text(if (state.busy) "Enviando teste…" else "Testar esta impressora") }
    } }
    if (confirmTest) AlertDialog(onDismissRequest = { confirmTest = false }, icon = { Icon(Icons.Outlined.Print, null) }, title = { Text("Enviar teste local?") }, text = { Text("Uma pequena página será enviada diretamente à impressora selecionada. Nenhum pedido será criado no servidor. Confirme visualmente a bobina, os acentos e o corte.") }, dismissButton = { TextButton(onClick = { confirmTest = false }) { Text("Cancelar") } }, confirmButton = { Button(onClick = { confirmTest = false; model.testPrinter() }) { Text("Enviar teste") } })
}

@Composable private fun ShiftCard(model: MainViewModel, granted: Boolean, bluetoothOn: Boolean) {
    val state = model.state
    var confirmUnlink by remember { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Settings, null); Text("Turno de impressão", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
        Text("Mantenha o telefone carregando e próximo da impressora. O serviço mostra uma notificação permanente enquanto o turno estiver ativo.")
        if (state.shiftActive) Button(onClick = model::stopShift, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Stop, null); Spacer(Modifier.width(8.dp)); Text("Encerrar turno") }
        else if (state.runtimeStatus == "INICIANDO") Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("Iniciando serviço…") }
        else {
            Button(onClick = model::startShift, enabled = granted && bluetoothOn && state.printerAddress.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (!bluetoothOn) "Ative o Bluetooth para iniciar" else "Iniciar turno") }
            OutlinedButton(onClick = { model.rotateCredential() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.Security, null); Spacer(Modifier.width(8.dp)); Text(if (state.busy) "Rotacionando…" else "Rotacionar credencial") }
            OutlinedButton(onClick = { confirmUnlink = true }, enabled = !state.busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Outlined.LinkOff, null); Spacer(Modifier.width(8.dp)); Text("Desvincular aparelho") }
        }
    } }
    if (confirmUnlink) AlertDialog(onDismissRequest = { confirmUnlink = false }, icon = { Icon(Icons.Outlined.LinkOff, null) }, title = { Text("Desvincular este aparelho?") }, text = { Text("A credencial será revogada no servidor e removida deste telefone. Será necessário gerar um novo código no painel para parear novamente.") }, dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar") } }, confirmButton = { Button(onClick = { confirmUnlink = false; model.unlink() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Desvincular") } })
}
