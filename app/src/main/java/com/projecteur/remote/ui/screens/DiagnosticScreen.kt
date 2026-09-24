package com.projecteur.remote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.diagnostics.CheckItem
import com.projecteur.remote.network.LocalAddress
import com.projecteur.remote.network.PingDiagnostic
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.Permissions
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import com.projecteur.remote.ui.StatusLine
import com.projecteur.remote.ui.theme.ErrorRed
import com.projecteur.remote.ui.theme.OkGreen
import com.projecteur.remote.ui.theme.WarnAmber
import kotlinx.coroutines.launch

/** 🧪 Diagnostic : checklist automatique + diagnostic réseau d'une adresse locale autorisée. */
@Composable
fun DiagnosticScreen(app: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nets by app.network.networks.collectAsState()
    var checks by remember { mutableStateOf<List<CheckItem>>(emptyList()) }
    var ip by remember { mutableStateOf(app.settings.pjlinkHost) }
    var count by remember { mutableStateOf(3) }
    var mode by remember { mutableStateOf(PingDiagnostic.Mode.ICMP) }
    var port by remember { mutableStateOf("4352") }
    var authorized by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<String>() }
    var report by remember { mutableStateOf<PingDiagnostic.Report?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runChecks() { checks = app.selfTest(Permissions.hasBluetooth(context)) }
    LaunchedEffect(Unit) { runChecks() }

    ScreenColumn {
        Section("Test automatique") {
            checks.forEach { c ->
                val color = when (c.status) {
                    CheckItem.Status.AVAILABLE -> OkGreen
                    CheckItem.Status.UNAVAILABLE -> ErrorRed
                    CheckItem.Status.NEEDS_ACCESSORY -> WarnAmber
                }
                StatusLine(c.status.symbol, "${c.title} — ${c.status.label}", c.detail, color)
            }
            Button(onClick = { runChecks() }) { Text("Relancer le test") }
        }

        Section("🧪 Diagnostic réseau") {
            if (nets.isEmpty()) {
                Notice("Diagnostic réseau indisponible : aucune interface réseau locale détectée.")
            } else {
            Text("Réseaux locaux : " + nets.joinToString { "${it.label} ${it.subnet}" }, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(ip, { ip = it }, label = { Text("Adresse IP locale") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Text("Nombre de requêtes :")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 3, 5, 10).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n") }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PingDiagnostic.Mode.entries.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) }) }
            }
            if (mode == PingDiagnostic.Mode.TCP) OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Port TCP (4352 = PJLink)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(authorized, { authorized = it })
                Text("Je suis autorisé(e) à tester cet appareil.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(enabled = authorized && !running, onClick = {
                error = null; report = null; lines.clear()
                app.network.refresh()
                when (val check = LocalAddress.check(ip, app.network.subnets())) {
                    is LocalAddress.Check.Refused -> error = check.reason
                    is LocalAddress.Check.Allowed -> {
                        val net = app.network.networkFor(check.subnet) ?: run { error = "Réseau introuvable"; return@Button }
                        val p = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: run { error = "Port invalide"; return@Button }
                        running = true
                        scope.launch {
                            report = runCatching {
                                app.ping.run(check.address, net, count, mode, p) { line -> scope.launch { lines += line } }
                            }.onFailure { error = it.message }.getOrNull()
                            running = false
                        }
                    }
                }
            }) { Text(if (running) "Test en cours…" else "Lancer") }
            error?.let { Text(it, color = ErrorRed) }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            report?.let { r ->
                fun ms(v: Double?) = v?.let { "%.1f ms".format(it) } ?: "—"
                Text("Requêtes envoyées : ${r.sent}", fontWeight = FontWeight.Medium)
                Text("Réponses reçues : ${r.received}")
                Text("Perte : %.0f %%".format(r.lossPercent))
                Text("Temps minimum : ${ms(r.min)}")
                Text("Temps moyen : ${ms(r.avg)}")
                Text("Temps maximum : ${ms(r.max)}")
            }
            }
            Text(
                "Une seule adresse privée du réseau local à la fois ; aucun balayage, aucune adresse Internet, " +
                    "aucun contournement de pare-feu.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
