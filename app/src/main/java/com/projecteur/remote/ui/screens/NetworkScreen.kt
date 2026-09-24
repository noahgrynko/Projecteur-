package com.projecteur.remote.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.projecteur.remote.AppContainer
import com.projecteur.remote.network.PjLink
import com.projecteur.remote.network.PjLinkTransport
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import kotlinx.coroutines.launch

/** Réseau local : uniquement si une interface existe réellement (Wi-Fi du projecteur, Ethernet USB-C…). */
@Composable
fun NetworkScreen(app: AppContainer, navigate: (String) -> Unit) {
    val nets by app.network.networks.collectAsState()
    val active by app.controller.active.collectAsState()
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(app.settings.pjlinkHost) }
    var password by remember { mutableStateOf(app.settings.pjlinkPassword) }
    var rememberPwd by remember { mutableStateOf(app.settings.rememberPassword) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    ScreenColumn {
        Section("Interfaces réseau locales") {
            if (nets.isEmpty()) Notice("Aucune interface réseau locale détectée. Le contrôle réseau est impossible tant que le " +
                "téléphone n'est pas relié au projecteur (Wi-Fi « point d'accès » du projecteur, ou adaptateur USB-C → Ethernet).")
            nets.forEach { Text("${it.label} (${it.interfaceName}) : ${it.subnet}" + if (it.hasInternet) "" else " — sans Internet") }
            OutlinedButton(onClick = { app.network.refresh() }) { Text("Actualiser") }
            Text(
                "Astuce : si le projecteur crée son propre Wi-Fi (mode « Simple AP »/« Quick Wireless »), rejoignez-le dans " +
                    "les réglages Android et choisissez « Rester connecté » quand Android signale l'absence d'Internet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Section("Contrôle PJLink (TCP 4352)") {
            OutlinedTextField(host, { host = it }, label = { Text("Adresse IP du projecteur") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Mot de passe PJLink (si défini)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(rememberPwd, { rememberPwd = it; app.settings.rememberPassword = it; if (!it) app.settings.pjlinkPassword = "" })
                Text("Mémoriser le mot de passe sur ce téléphone")
            }
            Button(enabled = !busy && nets.isNotEmpty(), onClick = {
                busy = true
                scope.launch {
                    val err = app.connectPjLink(host, password)
                    message = err ?: "Connecté par PJLink."
                    busy = false
                    if (err == null) navigate("remote")
                }
            }) { Text("Se connecter") }
            message?.let { Text(it) }
            (active as? PjLinkTransport)?.info?.let { i ->
                Text("Nom : ${i.name ?: "?"}")
                Text("Fabricant : ${i.manufacturer ?: "?"} — Modèle : ${i.product ?: "?"}")
                Text("Classe PJLink : ${i.pjlinkClass ?: "?"} — Alimentation : ${PjLink.powerLabel(i.power ?: "?")}")
                if (i.inputs.isNotEmpty()) Text("Entrées : " + i.inputs.joinToString { PjLink.inputTypeLabel(it) })
            }
            Text(
                "PJLink est la norme ouverte de contrôle réseau des vidéoprojecteurs. Elle couvre Power, Source/HDMI/VGA, " +
                    "Mute, Blank (classe 1) et Volume/Freeze (classe 2). Les menus et flèches n'en font pas partie. " +
                    "Seules les adresses privées du réseau local sont acceptées.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
