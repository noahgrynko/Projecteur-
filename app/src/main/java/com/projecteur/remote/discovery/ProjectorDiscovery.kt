package com.projecteur.remote.discovery

import com.projecteur.remote.bluetooth.BluetoothScanner
import com.projecteur.remote.bluetooth.BluetoothServices
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.ir.emitters.IrHardwareManager
import com.projecteur.remote.network.NetworkInspector
import com.projecteur.remote.network.PjLink
import com.projecteur.remote.network.PjLinkClient
import com.projecteur.remote.network.PjLinkDiscovery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.net.SocketFactory

/**
 * 🔎 Rechercher le vidéoprojecteur : essaie, dans l'ordre, chaque moyen réellement disponible.
 * Rien n'est inventé : un projecteur n'est « détecté » que s'il a répondu à un protocole
 * (PJLink). Un appareil Bluetooth n'est qu'un « candidat » (indice de nom/classe).
 */
class ProjectorDiscovery(
    private val scanner: BluetoothScanner,
    private val inspector: NetworkInspector,
    private val pjDiscovery: PjLinkDiscovery,
    private val hardware: IrHardwareManager,
) {
    enum class StepStatus(val symbol: String) { RUNNING("…"), OK("✓"), NONE("✗"), NEEDS_ACCESSORY("⚠"), SKIPPED("–") }

    data class Step(val title: String, val status: StepStatus, val detail: String)

    enum class Source { PJLINK, BLUETOOTH }

    data class Candidate(
        val source: Source,
        val confirmed: Boolean,
        val brand: String?,
        val model: String?,
        val method: String,
        val state: String,
        val address: String,
        val info: List<String>,
        val commands: Set<RemoteCommand>,
        val note: String? = null,
    )

    data class Report(val running: Boolean = false, val steps: List<Step> = emptyList(), val candidates: List<Candidate> = emptyList())

    private val _report = MutableStateFlow(Report())
    val report: StateFlow<Report> = _report.asStateFlow()

    private fun step(s: Step) = _report.update { r -> r.copy(steps = r.steps.filterNot { it.title == s.title } + s) }

    suspend fun run(bluetoothPermitted: Boolean, pjlinkPassword: String?) {
        _report.value = Report(running = true)
        val candidates = ArrayList<Candidate>()

        // 1. Bluetooth
        val btTitle = "1. Bluetooth"
        when {
            scanner.availability() == BluetoothScanner.Availability.NO_HARDWARE ->
                step(Step(btTitle, StepStatus.NONE, "Bluetooth indisponible sur cet appareil."))
            scanner.availability() == BluetoothScanner.Availability.DISABLED ->
                step(Step(btTitle, StepStatus.NONE, "Bluetooth désactivé. Activez-le puis relancez la recherche."))
            !bluetoothPermitted ->
                step(Step(btTitle, StepStatus.NONE, "Autorisation Bluetooth refusée : impossible de rechercher les appareils."))
            else -> {
                step(Step(btTitle, StepStatus.RUNNING, "Recherche des appareils Bluetooth (12 s)…"))
                scanner.scan(12_000)
                delay(12_500)
                val all = scanner.devices.value.values
                val probable = all.filter { it.probableProjector }
                probable.forEach { d ->
                    candidates += Candidate(
                        source = Source.BLUETOOTH, confirmed = false,
                        brand = null, model = d.name, method = "Bluetooth (${d.type})",
                        state = if (d.bonded) "Associé" else "Non associé",
                        address = d.address,
                        info = listOfNotNull(
                            BluetoothServices.deviceClassLabel(d.deviceClass)?.let { "Classe : $it" },
                            d.rssi?.let { "Signal : $it dBm" },
                        ) + d.uuids.map { "Service : " + BluetoothServices.describe(it).name },
                        commands = emptySet(),
                        note = "Indice seulement (nom/classe). Aucun service Bluetooth standard ne permet de contrôler un " +
                            "vidéoprojecteur ; seul le mode « télécommande HID » peut fonctionner, si le projecteur accepte " +
                            "les télécommandes/claviers Bluetooth. Inspectez ses services dans l'onglet Bluetooth.",
                    )
                }
                step(Step(btTitle, if (probable.isNotEmpty()) StepStatus.OK else StepStatus.NONE,
                    "${all.size} appareil(s) Bluetooth trouvé(s), dont ${probable.size} pouvant être un projecteur (indice)."))
            }
        }

        // 2. Réseau local
        val netTitle = "2. Réseau local (PJLink)"
        inspector.refresh()
        val nets = inspector.networks.value
        if (nets.isEmpty()) {
            step(Step(netTitle, StepStatus.NONE, "Aucune interface réseau locale détectée (pas de Wi-Fi, pas d'Ethernet USB-C)."))
        } else {
            step(Step(netTitle, StepStatus.RUNNING, "Recherche PJLink sur ${nets.joinToString { "${it.label} ${it.subnet}" }}…"))
            val found = pjDiscovery.search()
            for (f in found) {
                val factory = f.via.network?.socketFactory ?: SocketFactory.getDefault()
                val client = PjLinkClient(f.address, pjlinkPassword, factory)
                val info = runCatching { client.readInfo() }
                candidates += info.fold(
                    onSuccess = { i ->
                        Candidate(
                            source = Source.PJLINK, confirmed = true,
                            brand = i.manufacturer, model = i.product,
                            method = "PJLink classe ${i.pjlinkClass ?: "?"} (${f.via.label})",
                            state = PjLink.powerLabel(i.power ?: "?"),
                            address = f.address.hostAddress!!,
                            info = listOfNotNull(
                                i.name?.let { "Nom : $it" }, "MAC : ${f.mac}",
                                i.other?.let { "Infos : $it" },
                                i.inputs.takeIf { it.isNotEmpty() }?.let { "Entrées : " + it.joinToString { c -> PjLink.inputTypeLabel(c) } },
                                i.lamp?.let { "Lampe : $it" },
                            ),
                            commands = buildSet {
                                addAll(listOf(RemoteCommand.POWER, RemoteCommand.MUTE, RemoteCommand.BLANK, RemoteCommand.SOURCE))
                                if (i.pjlinkClass == "2") addAll(listOf(RemoteCommand.VOL_UP, RemoteCommand.VOL_DOWN, RemoteCommand.FREEZE))
                            },
                        )
                    },
                    onFailure = { e ->
                        Candidate(Source.PJLINK, true, null, null, "PJLink (${f.via.label})", "Réponse partielle",
                            f.address.hostAddress!!, listOf("MAC : ${f.mac}"), emptySet(), note = e.message)
                    },
                )
            }
            step(Step(netTitle, if (found.isNotEmpty()) StepStatus.OK else StepStatus.NONE,
                if (found.isNotEmpty()) "${found.size} projecteur(s) PJLink ont répondu."
                else "Aucune réponse PJLink classe 2. Un projecteur PJLink classe 1 peut encore être contacté en saisissant son adresse IP (onglet Réseau)."))
        }

        // 3. Connexion directe
        val directTitle = "3. Connexion directe"
        val ethernet = nets.filter { it.label.startsWith("Ethernet") }
        step(Step(directTitle, if (ethernet.isNotEmpty()) StepStatus.OK else StepStatus.NEEDS_ACCESSORY,
            if (ethernet.isNotEmpty()) "Adaptateur Ethernet USB-C actif : ${ethernet.joinToString { it.subnet.toString() }} (inclus dans la recherche réseau)."
            else "Aucune liaison directe : un adaptateur USB-C → Ethernet relié au port LAN du projecteur, ou le réseau Wi-Fi du projecteur, serait nécessaire."))

        // 4. Matériel IR
        val irTitle = "4. Matériel infrarouge"
        hardware.refresh()
        val hw = hardware.state.value
        step(when {
            hw.builtInEmitter -> Step(irTitle, StepStatus.OK, "Émetteur IR intégré disponible.")
            hw.active != null -> Step(irTitle, StepStatus.OK, "Émetteur IR externe actif : ${hw.active.name} (${hw.active.verification}).")
            else -> Step(irTitle, StepStatus.NEEDS_ACCESSORY,
                "Aucun émetteur infrarouge intégré détecté. Un émetteur IR externe compatible USB-C est nécessaire pour utiliser ce mode.")
        })

        // 5. Autres interfaces
        step(Step("5. Autres interfaces", StepStatus.SKIPPED,
            "Aucune autre interface officielle accessible à une application Android sans matériel (HDMI-CEC et RS-232 nécessitent un équipement dédié)."))

        _report.update { it.copy(running = false, candidates = candidates) }
    }
}
