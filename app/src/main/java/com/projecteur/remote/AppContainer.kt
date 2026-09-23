package com.projecteur.remote

import android.content.Context
import android.net.ConnectivityManager
import com.projecteur.remote.bluetooth.BluetoothHidRemote
import com.projecteur.remote.bluetooth.BluetoothScanner
import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.core.ControlMethod
import com.projecteur.remote.core.RemoteController
import com.projecteur.remote.data.Settings
import com.projecteur.remote.diagnostics.CheckItem
import com.projecteur.remote.diagnostics.CheckItem.Status
import com.projecteur.remote.discovery.ProjectorDiscovery
import com.projecteur.remote.ir.IrProfileRepository
import com.projecteur.remote.ir.IrTransport
import com.projecteur.remote.ir.emitters.IrHardwareManager
import com.projecteur.remote.network.NetworkInspector
import com.projecteur.remote.network.PingDiagnostic
import com.projecteur.remote.network.PjLinkClient
import com.projecteur.remote.network.PjLinkDiscovery
import com.projecteur.remote.network.PjLinkTransport
import com.projecteur.remote.network.LocalAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.net.SocketFactory

/** Assemble les composants de l'application (injection de dépendances manuelle). */
class AppContainer(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = Settings(context)
    val controller = RemoteController()
    val profiles = IrProfileRepository(context)
    val irHardware = IrHardwareManager(context, scope)
    val btScanner = BluetoothScanner(context, scope)
    val btRemote = BluetoothHidRemote(context)
    val network = NetworkInspector(context)
    val pjDiscovery = PjLinkDiscovery(network)
    val ping = PingDiagnostic(context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
    val discovery = ProjectorDiscovery(btScanner, network, pjDiscovery, irHardware)

    fun start() {
        irHardware.start()
        network.start()
        scope.launch { profiles.load() }
        // Si l'émetteur change (branchement/débranchement), la télécommande IR active est reconstruite.
        scope.launch {
            irHardware.state.collect { hw ->
                val active = controller.active.value
                if (active is IrTransport) {
                    if (hw.active == null) controller.deactivate() else activateIr()
                }
            }
        }
    }

    // --- Infrarouge ---------------------------------------------------------------------------

    /** Active la télécommande IR. Renvoie un message d'erreur clair si impossible. */
    fun activateIr(profileId: String? = settings.selectedProfileId): String? {
        val emitter = irHardware.state.value.active ?: return NO_IR_MESSAGE
        val profile = profiles.byId(profileId)
        val learned = profiles.learnedEntries()
        if (profile == null && learned.isEmpty()) {
            return "Aucun profil IR sélectionné : choisissez la marque/le modèle ou testez les profils compatibles."
        }
        if (profileId != null) settings.selectedProfileId = profileId
        controller.activate(IrTransport(emitter, profile, learned))
        return null
    }

    // --- Réseau (PJLink) ------------------------------------------------------------------------

    suspend fun connectPjLink(hostText: String, password: String): String? {
        network.refresh()
        val check = LocalAddress.check(hostText, network.subnets())
        if (check is LocalAddress.Check.Refused) return check.reason
        check as LocalAddress.Check.Allowed
        val net = network.networkFor(check.subnet)
        val factory = net?.network?.socketFactory ?: SocketFactory.getDefault()
        val transport = PjLinkTransport(PjLinkClient(check.address, password.ifEmpty { null }, factory))
        controller.activate(transport)
        settings.pjlinkHost = hostText
        if (settings.rememberPassword) settings.pjlinkPassword = password
        return transport.connect().exceptionOrNull()?.message
    }

    // --- Diagnostic automatique ----------------------------------------------------------------

    fun selfTest(bluetoothPermitted: Boolean): List<CheckItem> {
        network.refresh()
        irHardware.refresh()
        val hw = irHardware.state.value
        val items = ArrayList<CheckItem>()

        items += when (btScanner.availability()) {
            BluetoothScanner.Availability.NO_HARDWARE -> CheckItem("Bluetooth disponible", Status.UNAVAILABLE, "Aucun adaptateur Bluetooth.")
            BluetoothScanner.Availability.DISABLED -> CheckItem("Bluetooth disponible", Status.UNAVAILABLE, "Bluetooth désactivé.")
            BluetoothScanner.Availability.READY -> if (bluetoothPermitted)
                CheckItem("Bluetooth disponible", Status.AVAILABLE, "Activé, autorisations accordées.")
            else CheckItem("Bluetooth disponible", Status.UNAVAILABLE, "Autorisation Bluetooth refusée.")
        }
        items += when (btRemote.profile.value) {
            BluetoothHidRemote.ProfileStatus.READY, BluetoothHidRemote.ProfileStatus.REGISTERED ->
                CheckItem("Profil télécommande Bluetooth (HID Device)", Status.AVAILABLE, "Fourni par Android.")
            BluetoothHidRemote.ProfileStatus.UNSUPPORTED ->
                CheckItem("Profil télécommande Bluetooth (HID Device)", Status.UNAVAILABLE, "Non fourni par ce téléphone.")
            BluetoothHidRemote.ProfileStatus.UNKNOWN ->
                CheckItem("Profil télécommande Bluetooth (HID Device)", Status.UNAVAILABLE, "Non testé : ouvrez l'onglet Bluetooth.")
        }
        val nets = network.networks.value
        items += if (nets.isNotEmpty()) CheckItem("Réseau local disponible", Status.AVAILABLE,
            nets.joinToString { "${it.label} ${it.subnet}" + if (it.hasInternet) "" else " (sans Internet)" })
        else CheckItem("Réseau local disponible", Status.UNAVAILABLE, "Aucune interface réseau locale détectée.")

        items += if (hw.usb.isNotEmpty()) CheckItem("Périphérique USB détecté", Status.AVAILABLE,
            hw.usb.joinToString { "${it.description} [${it.ids}] : ${it.status.label}" })
        else CheckItem("Périphérique USB détecté", Status.NEEDS_ACCESSORY, "Aucun accessoire USB-C branché.")

        items += when {
            hw.builtInEmitter -> CheckItem("Émetteur IR disponible", Status.AVAILABLE, "Émetteur intégré au téléphone.")
            hw.active != null -> CheckItem("Émetteur IR disponible", Status.AVAILABLE, "${hw.active.kind.label} — ${hw.active.verification}")
            else -> CheckItem("Émetteur IR disponible", Status.NEEDS_ACCESSORY, NO_IR_MESSAGE)
        }
        items += if (hw.hasReceiver) CheckItem("Récepteur IR disponible", Status.AVAILABLE, hw.active!!.name)
        else CheckItem("Récepteur IR disponible", Status.NEEDS_ACCESSORY,
            "Un récepteur IR externe (USB IR Toy/Irdroid ou pont PROJIR avec TSOP) est nécessaire pour l'apprentissage.")

        val loaded = profiles.profiles.value
        items += if (loaded.isNotEmpty()) CheckItem("Profils IR chargés", Status.AVAILABLE,
            "${loaded.size} profils, ${loaded.map { it.brand }.distinct().size} marques (hors ligne).")
        else CheckItem("Profils IR chargés", Status.UNAVAILABLE, profiles.loadError.value ?: "Aucun profil chargé.")

        val report = discovery.report.value
        val confirmed = report.candidates.filter { it.confirmed }
        items += when {
            confirmed.isNotEmpty() -> CheckItem("Vidéoprojecteur détecté", Status.AVAILABLE,
                confirmed.joinToString { "${it.brand ?: "?"} ${it.model ?: ""} (${it.address})" })
            report.candidates.isNotEmpty() -> CheckItem("Vidéoprojecteur détecté", Status.UNAVAILABLE,
                "Seulement des candidats Bluetooth non confirmés : ${report.candidates.joinToString { it.model ?: it.address }}")
            report.steps.isEmpty() -> CheckItem("Vidéoprojecteur détecté", Status.UNAVAILABLE, "Recherche non lancée.")
            else -> CheckItem("Vidéoprojecteur détecté", Status.UNAVAILABLE, "Aucun projecteur trouvé.")
        }

        val active = controller.active.value
        items += when (val s = active?.state?.value) {
            is ConnectionState.Connected -> CheckItem("Connexion établie", Status.AVAILABLE, "${active.method.label} : ${s.detail}")
            is ConnectionState.ReadyOneWay -> CheckItem("Connexion établie", Status.UNAVAILABLE,
                "Infrarouge prêt, mais l'IR est unidirectionnel : aucune connexion ne peut être confirmée.")
            is ConnectionState.Error -> CheckItem("Connexion établie", Status.UNAVAILABLE, s.message)
            else -> CheckItem("Connexion établie", Status.UNAVAILABLE, "Aucune connexion.")
        }
        val commands = active?.supportedCommands?.value.orEmpty()
        items += if (commands.isNotEmpty()) CheckItem("Commandes disponibles", Status.AVAILABLE,
            "${commands.size} : " + commands.joinToString { it.label })
        else CheckItem("Commandes disponibles", Status.UNAVAILABLE, "Aucune méthode de contrôle active.")
        return items
    }

    val activeMethod: ControlMethod? get() = controller.active.value?.method

    companion object {
        const val NO_IR_MESSAGE =
            "Aucun émetteur infrarouge intégré détecté. Un émetteur IR externe compatible USB-C est nécessaire pour utiliser ce mode."
    }
}
