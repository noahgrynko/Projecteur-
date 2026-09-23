package com.projecteur.remote.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.projecteur.remote.AppContainer
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section

@Composable
fun ConfigScreen(app: AppContainer, navigate: (String) -> Unit) {
    ScreenColumn {
        FilledTonalButton(onClick = { navigate("ir") }, Modifier.fillMaxWidth()) { Text("🔴 Infrarouge (émetteur, profils)") }
        FilledTonalButton(onClick = { navigate("learn") }, Modifier.fillMaxWidth()) { Text("📡 Apprendre une commande IR") }
        FilledTonalButton(onClick = { navigate("bluetooth") }, Modifier.fillMaxWidth()) { Text("🔵 Bluetooth") }
        FilledTonalButton(onClick = { navigate("network") }, Modifier.fillMaxWidth()) { Text("🌐 Réseau local (PJLink)") }
        FilledTonalButton(onClick = { app.controller.deactivate() }, Modifier.fillMaxWidth()) { Text("Désactiver la méthode active") }
        Section("Ce que le Pixel 9a peut faire seul") {
            Text(
                "• Bluetooth : oui, mais uniquement comme télécommande HID pour les projecteurs qui acceptent " +
                    "télécommandes/claviers Bluetooth.\n" +
                    "• Réseau (PJLink) : oui, si le téléphone rejoint le Wi-Fi du projecteur ou un réseau local commun.\n" +
                    "• Infrarouge : NON — le Pixel 9a n'a pas d'émetteur IR. Un accessoire USB-C est nécessaire.\n" +
                    "• Apprentissage IR : NON — un récepteur IR USB est nécessaire.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Section("À propos") {
            Text(
                "Projecteur Remote fonctionne entièrement hors ligne. Il n'envoie que des commandes de télécommande et " +
                    "n'utilise jamais Cast, Miracast, AirPlay ni aucun partage d'écran.\n\n" +
                    "Codes IR : Flipper-IRDB (github.com/Lucaslhm/Flipper-IRDB), CC0 1.0.\n" +
                    "Pilotes série USB : usb-serial-for-android (MIT).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
