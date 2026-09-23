package com.projecteur.remote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.ir.FlipperIrFormat
import com.projecteur.remote.ir.IrDecoder
import com.projecteur.remote.ir.IrProtocols
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import kotlinx.coroutines.launch

/**
 * 📡 Apprendre une commande IR. Nécessite un récepteur IR réel (USB IR Toy/Irdroid ou pont
 * PROJIR avec récepteur TSOP). Sans récepteur, la fonction est indisponible — jamais simulée.
 */
@Composable
fun LearnScreen(app: AppContainer) {
    val hw by app.irHardware.state.collectAsState()
    val profiles by app.profiles.profiles.collectAsState()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(RemoteCommand.POWER.label) }
    var carrier by remember { mutableStateOf(38_000) }
    var listening by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<FlipperIrFormat.Entry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val learned = profiles.firstOrNull { it.id == com.projecteur.remote.ir.IrProfileRepository.LEARNED_ID }?.entries.orEmpty()

    ScreenColumn {
        val emitter = hw.active
        if (emitter == null || !emitter.canReceive) {
            Notice(
                "Un récepteur IR compatible est nécessaire pour apprendre une commande. Le Pixel 9a n'a ni émetteur ni " +
                    "récepteur infrarouge. Accessoires compatibles : USB IR Toy v2 / Irdroid USB IR Transceiver, ou le " +
                    "pont PROJIR (firmware/) équipé d'un récepteur TSOP38238."
            )
            if (emitter != null) Text("Émetteur actif « ${emitter.name} » : émission seulement.")
        } else {
            Section("1. Nom de la commande") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RemoteCommand.entries.forEach { c ->
                        FilterChip(selected = name == c.label, onClick = { name = c.label }, label = { Text(c.label) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nom (une touche de la télécommande ou un nom libre)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Porteuse (un récepteur démodulé ne peut pas la mesurer) :", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(36_000, 38_000, 40_000).forEach { f ->
                        FilterChip(selected = carrier == f, onClick = { carrier = f }, label = { Text("${f / 1000} kHz") })
                    }
                }
            }
            Section("2. Capture") {
                Text("Pointez la télécommande d'origine (ou une autre) vers le récepteur, à 5–10 cm, puis appuyez brièvement sur la touche.")
                Button(enabled = !listening && name.isNotBlank(), onClick = {
                    listening = true; captured = null; message = "En attente d'un signal (10 s)…"
                    scope.launch {
                        try {
                            val raw = emitter.receive(10_000)
                            if (raw == null) message = "Aucun signal reçu."
                            else {
                                val pattern = IrDecoder.trimCapture(raw)
                                val decoded = IrDecoder.decode(pattern)
                                captured = if (decoded != null) FlipperIrFormat.Entry(
                                    name = name.trim(), type = "parsed", protocol = decoded.protocol,
                                    address = decoded.address, command = decoded.command,
                                ) else FlipperIrFormat.Entry(
                                    name = name.trim(), type = "raw", frequency = carrier, data = pattern,
                                )
                                message = if (decoded != null) "Protocole reconnu : ${captured!!.describe()}"
                                else "Protocole non reconnu : signal brut conservé (${pattern.size} durées)."
                            }
                        } catch (e: Exception) {
                            message = "Erreur : ${e.message}"
                        } finally { listening = false }
                    }
                }) { Text("📡 Apprendre") }
                if (listening) CircularProgressIndicator()
                message?.let { Text(it) }
                captured?.let { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                message = runCatching { emitter.transmit(e.toSignal()); "Signal réémis." }
                                    .getOrElse { "Réémission impossible : ${it.message}" }
                            }
                        }) { Text("Tester (réémettre)") }
                        Button(onClick = {
                            scope.launch {
                                app.profiles.saveLearned(e)
                                message = "« ${e.name} » enregistrée. Elle remplace la touche correspondante du profil actif."
                                captured = null
                                if (app.controller.active.value is com.projecteur.remote.ir.IrTransport) app.activateIr()
                            }
                        }) { Text("Enregistrer") }
                    }
                }
            }
        }
        Section("Commandes apprises (${learned.size})") {
            if (learned.isEmpty()) Text("Aucune.")
            learned.forEach { e ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.Medium)
                        Text(e.describe(), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = emitter != null, onClick = {
                        scope.launch {
                            message = runCatching { emitter!!.transmit(e.toSignal()); "« ${e.name} » émis." }
                                .getOrElse { "Échec : ${it.message}" }
                        }
                    }) { Text("Émettre") }
                    TextButton(onClick = { scope.launch { app.profiles.deleteLearned(e.name) } }) { Text("Suppr.") }
                }
            }
            Text("Protocoles reconnus à l'apprentissage : ${IrProtocols.supported.joinToString()} ; les autres sont conservés en brut.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
