package com.projecteur.remote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.discovery.ProjectorDiscovery
import com.projecteur.remote.discovery.ProjectorDiscovery.StepStatus
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.Permissions
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import com.projecteur.remote.ui.StatusLine
import com.projecteur.remote.ui.theme.ErrorRed
import com.projecteur.remote.ui.theme.OkGreen
import com.projecteur.remote.ui.theme.WarnAmber
import kotlinx.coroutines.launch

@Composable
fun DiscoveryScreen(app: AppContainer, navigate: (String) -> Unit) {
    val context = LocalContext.current
    val report by app.discovery.report.collectAsState()
    val scope = rememberCoroutineScope()

    fun start() = scope.launch {
        app.discovery.run(Permissions.hasBluetooth(context), app.settings.pjlinkPassword.ifEmpty { null })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { start() }

    ScreenColumn {
        Button(
            onClick = {
                if (Permissions.hasBluetooth(context)) start() else permissionLauncher.launch(Permissions.bluetooth)
            },
            enabled = !report.running,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🔎 Rechercher le vidéoprojecteur") }
        Text(
            "Ordre : Bluetooth, réseau local (PJLink), connexion directe, matériel IR, autres interfaces. " +
                "Seules des méthodes locales et officielles sont utilisées ; rien n'est envoyé sur Internet.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (report.running) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(); Text("Recherche en cours…")
        }

        if (report.steps.isNotEmpty()) Section("Étapes") {
            report.steps.forEach { s ->
                val color = when (s.status) {
                    StepStatus.OK -> OkGreen
                    StepStatus.NEEDS_ACCESSORY, StepStatus.RUNNING -> WarnAmber
                    StepStatus.NONE -> ErrorRed
                    StepStatus.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                StatusLine(s.status.symbol, s.title, s.detail, color)
            }
        }

        if (!report.running && report.steps.isNotEmpty()) {
            if (report.candidates.isEmpty()) Notice(
                "Aucun projecteur trouvé. Sans réseau et sans Bluetooth de contrôle, le seul moyen restant est " +
                    "l'infrarouge : un émetteur IR externe USB-C est nécessaire avec le Pixel 9a."
            )
            report.candidates.forEach { c -> CandidateCard(app, c, navigate) }
        }
    }
}


@Composable
private fun CandidateCard(app: AppContainer, c: ProjectorDiscovery.Candidate, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    Section(if (c.confirmed) "Projecteur détecté" else "Appareil candidat (non confirmé)") {
        Text("Marque : ${c.brand ?: "inconnue"}", fontWeight = FontWeight.Medium)
        Text("Modèle : ${c.model ?: "inconnu"}")
        Text("Méthode : ${c.method}")
        Text("État : ${c.state}")
        Text("Adresse : ${c.address}")
        c.info.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Commandes disponibles : " + if (c.commands.isEmpty()) "aucune confirmée" else c.commands.joinToString { it.label },
            style = MaterialTheme.typography.bodySmall,
        )
        c.note?.let { Notice(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (c.source) {
                ProjectorDiscovery.Source.PJLINK -> Button(onClick = {
                    scope.launch {
                        app.connectPjLink(c.address, app.settings.pjlinkPassword)
                        navigate("remote")
                    }
                }) { Text("Utiliser (PJLink)") }
                ProjectorDiscovery.Source.BLUETOOTH -> Button(onClick = { navigate("bluetooth") }) { Text("Ouvrir dans Bluetooth") }
            }
            if (c.brand != null) OutlinedButton(onClick = {
                val match = app.profiles.bestMatch(c.brand, c.model)
                if (match != null) app.settings.selectedProfileId = match.id
                navigate("ir")
            }) { Text("Profils IR de la marque") }
        }
    }
}
