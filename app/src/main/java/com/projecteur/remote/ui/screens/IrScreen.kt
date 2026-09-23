package com.projecteur.remote.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projecteur.remote.AppContainer
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.core.SendResult
import com.projecteur.remote.ir.IrProfile
import com.projecteur.remote.ir.IrTransport
import com.projecteur.remote.ir.emitters.IrHardwareManager.UsbStatus
import com.projecteur.remote.ui.Notice
import com.projecteur.remote.ui.ScreenColumn
import com.projecteur.remote.ui.Section
import com.projecteur.remote.ui.StatusLine
import com.projecteur.remote.ui.theme.ErrorRed
import com.projecteur.remote.ui.theme.OkGreen
import com.projecteur.remote.ui.theme.WarnAmber
import kotlinx.coroutines.launch

/** 🔴 Infrarouge : matériel, profils, sélection automatique, test guidé des profils. */
@Composable
fun IrScreen(app: AppContainer, navigate: (String) -> Unit) {
    val hw by app.irHardware.state.collectAsState()
    val allProfiles by app.profiles.profiles.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by remember { mutableStateOf(app.settings.selectedProfileId) }
    val selected = allProfiles.firstOrNull { it.id == selectedId }
    var info by remember { mutableStateOf<String?>(null) }
    var audioConfirm by remember { mutableStateOf<Int?>(null) }
    var brandFilter by remember { mutableStateOf<String?>(null) }
    var brandPicker by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var knownBrand by remember { mutableStateOf("") }
    var knownModel by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<IrProfile?>(null) }
    var testCommand by remember { mutableStateOf(RemoteCommand.POWER) }
    var wizard by remember { mutableStateOf<List<IrProfile>?>(null) }
    var wizardIndex by remember { mutableStateOf(0) }
    var wizardMessage by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "import.ir"
            info = app.profiles.import(uri, name).fold(
                onSuccess = { selectedId = it.id; app.settings.selectedProfileId = it.id; "Profil importé : ${it.displayName}" },
                onFailure = { "Import impossible : ${it.message}" },
            )
        }
    }

    fun select(p: IrProfile) {
        selectedId = p.id
        app.settings.selectedProfileId = p.id
        info = "Profil sélectionné : ${p.displayName}"
    }

    ScreenColumn {
        Section("Émetteur infrarouge") {
            StatusLine(
                if (hw.builtInEmitter) "✓" else "✗", "Émetteur IR intégré",
                if (hw.builtInEmitter) "Présent (ConsumerIrManager)." else "Absent sur ce téléphone (c'est le cas du Google Pixel 9a).",
                if (hw.builtInEmitter) OkGreen else ErrorRed,
            )
            val active = hw.active
            if (active == null) {
                Notice("Aucun émetteur infrarouge intégré détecté. Un émetteur IR externe compatible USB-C est nécessaire pour utiliser ce mode.")
            } else {
                StatusLine("✓", "Émetteur actif : ${active.kind.label}", "${active.name}\n${active.verification}\n" +
                    if (active.canReceive) "Émission et réception (apprentissage possible)." else "Émission seulement.", OkGreen)
                if (active.kind != com.projecteur.remote.ir.emitters.IrEmitter.Kind.BUILT_IN)
                    OutlinedButton(onClick = { app.irHardware.deactivate() }) { Text("Désactiver cet émetteur") }
            }
            hw.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
            Text("Périphériques USB-C", fontWeight = FontWeight.Medium)
            if (hw.usb.isEmpty()) Text("Aucun périphérique USB branché.", style = MaterialTheme.typography.bodySmall)
            hw.usb.forEach { u ->
                val color = when (u.status) {
                    UsbStatus.VERIFIED -> OkGreen
                    UsbStatus.PROBING, UsbStatus.NEEDS_PERMISSION -> WarnAmber
                    else -> ErrorRed
                }
                StatusLine("•", "${u.description} [${u.ids}]", u.status.label + (u.detail?.let { " — $it" } ?: ""), color)
                if (u.candidate != null && u.status in setOf(UsbStatus.NEEDS_PERMISSION, UsbStatus.PERMISSION_DENIED, UsbStatus.INCOMPATIBLE)) {
                    OutlinedButton(onClick = { app.irHardware.requestPermission(u.deviceName) }) { Text("Autoriser et vérifier") }
                }
            }
            OutlinedButton(onClick = { app.irHardware.refresh() }) { Text("Actualiser") }
            if (hw.audioOutputs.isNotEmpty()) {
                HorizontalDivider()
                Text("Sorties audio filaires (émetteur IR « jack »)", fontWeight = FontWeight.Medium)
                hw.audioOutputs.forEach { a ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${a.name} (${a.typeLabel})", Modifier.weight(1f))
                        OutlinedButton(onClick = { audioConfirm = a.id }) { Text("Utiliser") }
                    }
                }
            }
        }

        Section("Profil de télécommande") {
            if (selected == null) Text("Aucun profil sélectionné.")
            else {
                Text(selected.displayName, fontWeight = FontWeight.SemiBold)
                Text("Source : ${selected.source.label}", style = MaterialTheme.typography.bodySmall)
                Text("Protocole(s) : ${selected.protocolSummary} — porteuse : ${selected.carrierSummary}", style = MaterialTheme.typography.bodySmall)
                Text("Touches reconnues : " + selected.commands.keys.joinToString { it.label }.ifEmpty { "aucune" }, style = MaterialTheme.typography.bodySmall)
                if (selected.unsupportedCount > 0) Text(
                    "${selected.unsupportedCount} signal(aux) utilisent un protocole non pris en charge et sont ignorés.",
                    style = MaterialTheme.typography.bodySmall, color = WarnAmber,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { detail = selected }) { Text("Tous les signaux") }
                }
            }
            Button(onClick = {
                val err = app.activateIr(selectedId)
                info = err ?: "Télécommande infrarouge active."
                if (err == null) navigate("remote")
            }, Modifier.fillMaxWidth()) { Text("🔴 Utiliser l'infrarouge avec ce profil") }
            info?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Section("Marque et modèle connus") {
            OutlinedTextField(knownBrand, { knownBrand = it }, label = { Text("Marque (ex. Epson)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(knownModel, { knownModel = it }, label = { Text("Modèle (ex. EB-X12)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val match = app.profiles.bestMatch(knownBrand, knownModel)
                if (match != null) select(match) else {
                    brandFilter = app.profiles.brands.firstOrNull { it.equals(knownBrand.trim(), true) }
                    query = if (brandFilter == null) "$knownBrand $knownModel".trim() else knownModel
                    info = "Pas de correspondance exacte : voir la liste des profils compatibles ci-dessous et les tester."
                }
            }) { Text("Sélection automatique") }
        }

        Section("Rechercher / tester des profils") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { brandPicker = true }) { Text(brandFilter ?: "Toutes les marques") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text("Importer .ir") }
            }
            OutlinedTextField(query, { query = it }, label = { Text("Recherche (marque, modèle…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val results = app.profiles.search(query, brandFilter).filter { it.source != IrProfile.Source.LEARNED }
            Text("${results.size} profil(s)", style = MaterialTheme.typography.bodySmall)
            Text("Commande de test :", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(RemoteCommand.POWER, RemoteCommand.MENU, RemoteCommand.SOURCE, RemoteCommand.BLANK).forEach { c ->
                    FilterChip(selected = testCommand == c, onClick = { testCommand = c }, label = { Text(c.label) })
                }
            }
            Button(
                onClick = {
                    if (hw.active == null) { info = com.projecteur.remote.AppContainer.NO_IR_MESSAGE; return@Button }
                    val testable = results.filter { testCommand in it.commands }
                    if (testable.isEmpty()) { info = "Aucun profil compatible avec la touche « ${testCommand.label} » dans cette liste."; return@Button }
                    wizard = testable; wizardIndex = 0; wizardMessage = null
                },
                enabled = results.isNotEmpty(),
            ) { Text("Tester ces profils un par un") }
            results.take(60).forEach { p ->
                Column(Modifier.fillMaxWidth().clickable { select(p) }.padding(vertical = 4.dp)) {
                    Text(p.displayName + if (p.id == selectedId) "  ✓" else "", fontWeight = FontWeight.Medium)
                    Text("${p.protocolSummary} • ${p.commands.size} touches reconnues", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (results.size > 60) Text("… affinez la recherche pour voir les autres profils.", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Codes IR : base communautaire Flipper-IRDB (CC0), fichiers intégrés sans modification. " +
                "Aucun code n'est inventé : une touche absente d'un profil reste désactivée.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (brandPicker) AlertDialog(
        onDismissRequest = { brandPicker = false },
        confirmButton = { TextButton(onClick = { brandFilter = null; brandPicker = false }) { Text("Toutes") } },
        title = { Text("Marque") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(app.profiles.brands) { b ->
                    Text(b, Modifier.fillMaxWidth().clickable { brandFilter = b; brandPicker = false }.padding(10.dp))
                }
            }
        },
    )

    audioConfirm?.let { id ->
        AlertDialog(
            onDismissRequest = { audioConfirm = null },
            title = { Text("Émetteur IR audio") },
            text = {
                Text(
                    "Ce mode suppose qu'un émetteur IR à LED pour prise jack (deux LED IR tête-bêche sur les voies " +
                        "gauche/droite) est branché via l'adaptateur USB-C. Android ne peut PAS vérifier sa présence : " +
                        "seul un essai sur le projecteur le confirmera. Mettez le volume média au maximum et ne branchez " +
                        "jamais un casque dans ce mode."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    info = if (app.irHardware.activateAudio(id)) "Émetteur IR audio activé (non vérifiable)." else "Sortie audio introuvable."
                    audioConfirm = null
                }) { Text("J'ai branché un émetteur IR jack") }
            },
            dismissButton = { TextButton(onClick = { audioConfirm = null }) { Text("Annuler") } },
        )
    }

    detail?.let { p ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(p.displayName) },
            text = {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    if (p.comments.isNotEmpty()) item { Text(p.comments.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
                    items(p.entries) { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontWeight = FontWeight.Medium)
                                Text(e.describe() + if (!e.isSupported) " — non pris en charge" else "", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(enabled = e.isSupported && hw.active != null, onClick = {
                                val emitter = hw.active ?: return@TextButton
                                scope.launch {
                                    info = when (val r = IrTransport(emitter, p, emptyList()).sendEntry(e)) {
                                        is SendResult.Sent -> r.detail
                                        is SendResult.Failed -> r.message
                                        SendResult.Unsupported -> "Non pris en charge"
                                    }
                                }
                            }) { Text("Émettre") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Fermer") } },
        )
    }

    wizard?.let { list ->
        val p = list.getOrNull(wizardIndex)
        if (p == null) {
            AlertDialog(
                onDismissRequest = { wizard = null },
                title = { Text("Fin du test") },
                text = { Text("Aucun des ${list.size} profils testés n'a été confirmé. Aucun profil compatible : essayez une autre marque, importez un fichier .ir, ou apprenez les codes avec un récepteur IR.") },
                confirmButton = { TextButton(onClick = { wizard = null }) { Text("OK") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Test ${wizardIndex + 1} / ${list.size}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.displayName, fontWeight = FontWeight.SemiBold)
                        Text("1. Visez le projecteur. 2. Appuyez sur « Envoyer ». 3. Indiquez s'il a réagi (${testCommand.label}).")
                        wizardMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        OutlinedButton(onClick = {
                            val emitter = hw.active ?: return@OutlinedButton
                            scope.launch {
                                wizardMessage = when (val r = IrTransport(emitter, p, emptyList()).send(testCommand)) {
                                    is SendResult.Sent -> "Envoyé : ${r.detail}. Le projecteur a-t-il réagi ?"
                                    is SendResult.Failed -> "Échec : ${r.message}"
                                    SendResult.Unsupported -> "Touche absente de ce profil"
                                }
                            }
                        }) { Text("Envoyer « ${testCommand.label} »") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { select(p); wizard = null; info = "Profil confirmé par l'utilisateur : ${p.displayName}" }) { Text("Oui, il a réagi") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { wizard = null }) { Text("Arrêter") }
                        TextButton(onClick = { wizardIndex++; wizardMessage = null }) { Text("Non, suivant") }
                    }
                },
            )
        }
    }
}
