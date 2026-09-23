package com.projecteur.remote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.projecteur.remote.core.ConnectionState
import com.projecteur.remote.core.ControlMethod
import com.projecteur.remote.core.ControlTransport
import com.projecteur.remote.core.RemoteCommand
import com.projecteur.remote.core.SendResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Mode 🔵 Bluetooth : le téléphone se présente comme une télécommande/un clavier Bluetooth
 * standard (profil HID Device, API officielle android.bluetooth.BluetoothHidDevice, Android 9+).
 *
 * Cela fonctionne uniquement avec les projecteurs qui acceptent des télécommandes ou claviers
 * Bluetooth (en pratique : projecteurs « smart » sous Android/Android TV/Google TV). Les
 * projecteurs dont le Bluetooth sert seulement au son ne peuvent PAS être contrôlés ainsi.
 *
 * L'association passe par la procédure normale (confirmation/code sur le projecteur si demandé).
 * « Connecté » n'est affiché qu'après l'évènement STATE_CONNECTED de la pile Bluetooth.
 */
@SuppressLint("MissingPermission")
class BluetoothHidRemote(private val context: Context) : ControlTransport {
    override val method = ControlMethod.BLUETOOTH_HID
    override var title = "Télécommande Bluetooth HID"
        private set

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    private val _supported = MutableStateFlow<Set<RemoteCommand>>(emptySet())
    override val supportedCommands: StateFlow<Set<RemoteCommand>> = _supported.asStateFlow()

    enum class ProfileStatus { UNKNOWN, UNSUPPORTED, READY, REGISTERED }
    private val _profile = MutableStateFlow(ProfileStatus.UNKNOWN)
    val profile: StateFlow<ProfileStatus> = _profile.asStateFlow()

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var pendingTarget: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _profile.value = if (registered) ProfileStatus.REGISTERED else ProfileStatus.READY
            if (registered) {
                pendingTarget?.let { hid?.connect(it) }
                pendingTarget = null
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            val name = runCatching { device.name }.getOrNull() ?: device.address
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    title = "Bluetooth HID → $name"
                    _supported.value = HidReports.mapping.keys
                    _state.value = ConnectionState.Connected("Connecté à $name en tant que télécommande HID")
                }
                BluetoothProfile.STATE_CONNECTING ->
                    _state.value = ConnectionState.Connecting("Connexion à $name… (acceptez sur le projecteur si demandé)")
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host == device || host == null) {
                        host = null
                        _supported.value = emptySet()
                        _state.value = ConnectionState.Error(
                            "Non connecté à $name. Le projecteur n'accepte peut-être pas les télécommandes Bluetooth, " +
                                "ou l'association doit être confirmée sur le projecteur."
                        )
                    }
                }
            }
        }
    }

    /** Ouvre le profil HID Device ; renvoie false si le téléphone ne le fournit pas. */
    fun open(): Boolean {
        val a = adapter ?: run { _profile.value = ProfileStatus.UNSUPPORTED; return false }
        if (hid != null) return true
        val ok = a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                _profile.value = ProfileStatus.READY
                register()
            }
            override fun onServiceDisconnected(profile: Int) {
                hid = null
                _profile.value = ProfileStatus.UNKNOWN
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) _profile.value = ProfileStatus.UNSUPPORTED
        return ok
    }

    private fun register() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Projecteur Remote", "Télécommande de vidéoprojecteur", "Projecteur Remote",
            BluetoothHidDevice.SUBCLASS1_COMBO, HidReports.DESCRIPTOR,
        )
        val ok = hid?.registerApp(sdp, null, null, executor, callback) ?: false
        if (!ok) _state.value = ConnectionState.Error("Impossible d'enregistrer la télécommande HID (profil occupé par une autre application ?)")
    }

    /** Se connecte à un appareil déjà associé (l'association elle-même se fait via le système). */
    fun connect(device: BluetoothDevice) {
        _state.value = ConnectionState.Connecting("Connexion HID à ${runCatching { device.name }.getOrNull() ?: device.address}…")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            _state.value = ConnectionState.Error("Appareil non associé : lancez l'association (un code ou une confirmation peut être demandé sur le projecteur).")
            return
        }
        val h = hid
        if (h == null || _profile.value != ProfileStatus.REGISTERED) {
            pendingTarget = device
            if (h == null) open()
            return
        }
        if (!h.connect(device)) _state.value = ConnectionState.Error("La pile Bluetooth a refusé la connexion HID.")
    }

    fun connect(address: String) {
        val d = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        connect(d)
    }

    override suspend fun send(command: RemoteCommand): SendResult {
        val usage = HidReports.mapping[command] ?: return SendResult.Unsupported
        val h = hid
        val d = host
        if (h == null || d == null || _state.value !is ConnectionState.Connected) {
            return SendResult.Failed("Télécommande Bluetooth non connectée.")
        }
        val (id, press) = HidReports.press(usage)
        if (!h.sendReport(d, id, press)) return SendResult.Failed("Envoi Bluetooth refusé (connexion perdue ?)")
        delay(60)
        val (rid, release) = HidReports.release(usage)
        h.sendReport(d, rid, release)
        return SendResult.Sent("touche HID envoyée")
    }

    override fun close() {
        host?.let { d -> runCatching { hid?.disconnect(d) } }
        host = null
        runCatching { hid?.unregisterApp() }
        hid?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        hid = null
        _profile.value = ProfileStatus.UNKNOWN
        _supported.value = emptySet()
        _state.value = ConnectionState.Disconnected
    }
}
