package com.projecteur.remote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.core.ControlMethod
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.network.PjLinkTransport
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.describe
import com.projecteur.remote.ui.theme.PowerRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Télécommande. Les touches non prises en charge par la méthode active sont désactivées. */
@Composable
fun RemoteScreen(app: AppContainer, navigate: (String) -> Unit) {
    val active by app.controller.active.collectAsState()
    val noState = remember { MutableStateFlow<ConnectionState>(ConnectionState.Disconnected) }
    val noCommands = remember { MutableStateFlow<Set<RemoteCommand>>(emptySet()) }
    val state by (active?.state ?: noState).collectAsState()
    val supported by (active?.supportedCommands ?: noCommands).collectAsState()
    val message by app.controller.lastMessage.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var confirmPowerOff by remember { mutableStateOf(false) }

    fun send(c: RemoteCommand) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { app.controller.send(c) }
    }

    ScreenColumn {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val (label, color) = state.describe()
                Text("Méthode : ${active?.method?.label ?: "aucune"}", fontWeight = FontWeight.SemiBold)
                active?.let { Text(it.title, style = MaterialTheme.typography.bodySmall) }
                Text("État : $label", color = color, style = MaterialTheme.typography.bodyMedium)
                if (active == null) Text(
                    "Aucune méthode active. Lancez une recherche ou configurez l'infrarouge, le Bluetooth ou le réseau.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { navigate("discovery") }, Modifier.weight(1f)) { Text("🔎 Rechercher") }
            FilledTonalButton(onClick = { navigate("config") }, Modifier.weight(1f)) { Text("⚙ Configuration") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(onClick = { navigate("learn") }, Modifier.weight(1f)) { Text("📡 Apprentissage IR") }
            FilledTonalButton(onClick = { navigate("diagnostic") }, Modifier.weight(1f)) { Text("🧪 Diagnostic") }
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RoundKey("⏻", RemoteCommand.POWER in supported, size = 72.dp, color = PowerRed) {
                    if (active is PjLinkTransport) {
                        confirmPowerOff = true
                    } else send(RemoteCommand.POWER)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillKey("VOL −", RemoteCommand.VOL_DOWN in supported) { send(RemoteCommand.VOL_DOWN) }
                    PillKey("MUTE", RemoteCommand.MUTE in supported) { send(RemoteCommand.MUTE) }
                    PillKey("VOL +", RemoteCommand.VOL_UP in supported) { send(RemoteCommand.VOL_UP) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillKey("SOURCE", RemoteCommand.SOURCE in supported) { send(RemoteCommand.SOURCE) }
                    if (RemoteCommand.HDMI in supported) PillKey("HDMI", true) { send(RemoteCommand.HDMI) }
                    if (RemoteCommand.VGA in supported) PillKey("VGA", true) { send(RemoteCommand.VGA) }
                }
                RoundKey("▲", RemoteCommand.UP in supported) { send(RemoteCommand.UP) }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundKey("◀", RemoteCommand.LEFT in supported) { send(RemoteCommand.LEFT) }
                    RoundKey("OK", RemoteCommand.OK in supported, size = 76.dp) { send(RemoteCommand.OK) }
                    RoundKey("▶", RemoteCommand.RIGHT in supported) { send(RemoteCommand.RIGHT) }
                }
                RoundKey("▼", RemoteCommand.DOWN in supported) { send(RemoteCommand.DOWN) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillKey("MENU", RemoteCommand.MENU in supported) { send(RemoteCommand.MENU) }
                    PillKey("RETOUR", RemoteCommand.BACK in supported) { send(RemoteCommand.BACK) }
                    PillKey("EXIT", RemoteCommand.EXIT in supported) { send(RemoteCommand.EXIT) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillKey("FREEZE", RemoteCommand.FREEZE in supported) { send(RemoteCommand.FREEZE) }
                    PillKey("BLANK", RemoteCommand.BLANK in supported) { send(RemoteCommand.BLANK) }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (active?.method == ControlMethod.INFRARED) Text(
            "Infrarouge : visez le capteur IR du projecteur (souvent à l'avant ou à l'arrière), à moins de 5–8 m. " +
                "Le projecteur ne peut pas confirmer la réception. Pour éteindre, beaucoup de modèles demandent d'appuyer deux fois sur Power.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    }

    if (confirmPowerOff) AlertDialog(
        onDismissRequest = { confirmPowerOff = false },
        title = { Text("Alimentation du projecteur") },
        text = { Text("Allumé, il passera en veille (refroidissement) ; en veille, il s'allumera. Continuer ?") },
        confirmButton = { TextButton(onClick = { confirmPowerOff = false; send(RemoteCommand.POWER) }) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = { confirmPowerOff = false }) { Text("Annuler") } },
    )
}

@Composable
private fun RoundKey(label: String, enabled: Boolean, size: Dp = 64.dp, color: Color? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = Modifier.size(size),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
        else ButtonDefaults.buttonColors(),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PillKey(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(50)) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}
