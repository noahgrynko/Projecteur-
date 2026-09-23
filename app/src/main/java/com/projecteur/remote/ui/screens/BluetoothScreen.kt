package com.projecteur.remote.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.bluetooth.BluetoothHidRemote
import com.projecteur.remote.bluetooth.BluetoothScanner
import com.projecteur.remote.bluetooth.BluetoothServices
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.Permissions
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import com.projecteur.remote.ui.describe
import com.projecteur.remote.ui.theme.ErrorRed

/** 🔵 Bluetooth : recherche, inspection des services et mode télécommande HID. */
@Composable
fun BluetoothScreen(app: AppContainer, navigate: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.hasBluetooth(context)) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val devices by app.btScanner.devices.collectAsState()
    val scanning by app.btScanner.scanning.collectAsState()
    val scanMessage by app.btScanner.message.collectAsState()
    val hidProfile by app.btRemote.profile.collectAsState()
    val hidState by app.btRemote.state.collectAsState()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = Permissions.hasBluetooth(context)
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshTick++ }
    val discoverableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    LaunchedEffect(granted) { if (granted) app.btRemote.open() }

    ScreenColumn {
        val availability = remember(refreshTick, granted) { app.btScanner.availability() }
        Notice(
            "Il n'existe pas de profil Bluetooth standard pour « contrôler un vidéoprojecteur ». Sur la plupart des " +
                "projecteurs, le Bluetooth sert uniquement au son : il ne permet pas de les commander. Le seul moyen " +
                "officiel utilisable ici est le mode « télécommande HID » : le téléphone se présente comme une " +
                "télécommande/un clavier Bluetooth, ce qui fonctionne avec les projecteurs qui acceptent ces " +
                "accessoires (projecteurs « smart » Android/Google TV). Aucun partage d'écran n'est utilisé."
        )
        when {
            !granted -> Section("Autorisation") {
                Text("L'application a besoin des autorisations « Appareils à proximité » pour rechercher et se connecter.")
                Button(onClick = { permLauncher.launch(Permissions.bluetooth) }) { Text("Autoriser") }
                Text("Si vous avez refusé définitivement : Paramètres → Applications → Projecteur Remote → Autorisations.",
                    style = MaterialTheme.typography.bodySmall)
            }
            availability == BluetoothScanner.Availability.NO_HARDWARE -> Notice("Bluetooth indisponible sur cet appareil.", ErrorRed)
            availability == BluetoothScanner.Availability.DISABLED -> Section("Bluetooth désactivé") {
                Button(onClick = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) { Text("Activer le Bluetooth") }
            }
            else -> {
                Section("Télécommande HID") {
                    Text(when (hidProfile) {
                        BluetoothHidRemote.ProfileStatus.UNSUPPORTED -> "Profil HID Device non fourni par ce téléphone : mode indisponible."
                        BluetoothHidRemote.ProfileStatus.UNKNOWN -> "Ouverture du profil HID…"
                        BluetoothHidRemote.ProfileStatus.READY -> "Profil disponible, enregistrement…"
                        BluetoothHidRemote.ProfileStatus.REGISTERED -> "Prêt : le téléphone peut se présenter comme télécommande."
                    })
                    val (label, color) = hidState.describe()
                    Text("État : $label", color = color)
                    Text(
                        "Association : soit depuis le projecteur (menu Bluetooth → ajouter une télécommande/un accessoire, " +
                            "en rendant le téléphone visible), soit depuis la liste ci-dessous (« Associer »). Si un code ou " +
                            "une confirmation s'affiche, validez-le sur les deux appareils : aucune authentification n'est contournée.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        discoverableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120))
                    }) { Text("Rendre le téléphone visible (120 s)") }
                    if (hidState is com.projecteur.remote.core.ConnectionState.Connected) Button(onClick = {
                        app.controller.activate(app.btRemote); navigate("remote")
                    }) { Text("Utiliser comme télécommande") }
                }
                Section("Appareils Bluetooth") {
                    Button(onClick = { app.btScanner.scan() }, enabled = !scanning) { Text("Rechercher (12 s)") }
                    if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    scanMessage?.let { Text(it) }
                    devices.values.sortedWith(compareByDescending<BluetoothScanner.Device> { it.probableProjector }
                        .thenByDescending { it.bonded }.thenByDescending { it.rssi ?: -200 }).forEach { d ->
                        HorizontalDivider()
                        Text(d.displayName + if (d.probableProjector) "  (projecteur possible)" else "", fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(
                            d.address, d.type, if (d.bonded) "associé" else "non associé",
                            BluetoothServices.deviceClassLabel(d.deviceClass), d.rssi?.let { "$it dBm" },
                        ).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                        if (d.inspecting) Text("Lecture des services…", style = MaterialTheme.typography.bodySmall)
                        d.inspectError?.let { Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall) }
                        if (d.uuids.isNotEmpty()) {
                            val services = d.uuids.map(BluetoothServices::describe)
                            services.forEach { s -> Text("– ${s.name} : ${s.explanation}", style = MaterialTheme.typography.bodySmall) }
                            Text(
                                "Conclusion : aucun service de contrôle de projecteur standard. " +
                                    if (services.any { it.meaning == BluetoothServices.Meaning.AUDIO_ONLY })
                                        "Le Bluetooth de cet appareil sert au son ; le contrôle n'est possible que s'il accepte une télécommande HID."
                                    else "Seul le mode télécommande HID peut être essayé.",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                            )
                        } else if (d.inspected) Text("Aucun service publié.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { app.btScanner.inspect(d.address) }) { Text("Services") }
                            if (!d.bonded) OutlinedButton(onClick = { app.btScanner.pair(d.address) }) { Text("Associer") }
                            else OutlinedButton(
                                enabled = hidProfile == BluetoothHidRemote.ProfileStatus.REGISTERED,
                                onClick = { app.settings.lastBluetoothHost = d.address; app.btRemote.connect(d.address) },
                            ) { Text("Connecter HID") }
                        }
                    }
                }
            }
        }
    }
}
