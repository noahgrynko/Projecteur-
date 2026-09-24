package com.projecteur.remote.ir.emitters

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Détecte le matériel IR réellement présent : émetteur intégré (ConsumerIrManager),
 * périphériques USB (API Android USB Host) et sorties audio filaires.
 * Un périphérique n'est déclaré « compatible » qu'après une réponse correcte à son protocole.
 */
class IrHardwareManager(private val context: Context, private val scope: CoroutineScope) {

    enum class UsbStatus(val label: String) {
        UNKNOWN_DEVICE("Périphérique USB non reconnu (aucun pilote série)"),
        NEEDS_PERMISSION("Autorisation USB nécessaire"),
        PERMISSION_DENIED("Autorisation USB refusée"),
        PROBING("Vérification du protocole…"),
        VERIFIED("Compatible — vérifié"),
        INCOMPATIBLE("Émetteur IR externe non compatible"),
    }

    data class UsbEntry(
        val deviceName: String,
        val vendorId: Int,
        val productId: Int,
        val description: String,
        val candidate: IrEmitter.Kind?,
        val status: UsbStatus,
        val detail: String? = null,
    ) {
        val ids get() = "%04X:%04X".format(vendorId, productId)
    }

    data class AudioOutput(val id: Int, val name: String, val typeLabel: String)

    data class State(
        val builtInFeature: Boolean = false,
        val builtInEmitter: Boolean = false,
        val usb: List<UsbEntry> = emptyList(),
        val audioOutputs: List<AudioOutput> = emptyList(),
        val active: IrEmitter? = null,
        val message: String? = null,
    ) {
        val hasEmitter get() = active != null
        val hasReceiver get() = active?.canReceive == true
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var builtIn: BuiltInIrEmitter? = null

    private val prober = UsbSerialProber(
        UsbSerialProber.getDefaultProbeTable().apply {
            addProduct(IrToyEmitter.VENDOR_ID, IrToyEmitter.PRODUCT_ID, CdcAcmSerialDriver::class.java)
        }
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = intent.usbDevice() ?: return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        scope.launch { probeUsb(device) }
                    } else {
                        setUsbStatus(device, UsbStatus.PERMISSION_DENIED,
                            "Autorisation refusée. Débranchez/rebranchez l'accessoire puis acceptez la demande.")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> refresh()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.usbDevice()
                    val active = _state.value.active
                    if (active != null && active.kind != IrEmitter.Kind.BUILT_IN && active.kind != IrEmitter.Kind.AUDIO &&
                        device != null && activeDeviceName == device.deviceName
                    ) {
                        active.close()
                        activeDeviceName = null
                        _state.update { it.copy(active = null, message = "Émetteur IR USB débranché.") }
                    }
                    refresh()
                }
            }
        }
    }
    private var activeDeviceName: String? = null

    fun start() {
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        val probe = BuiltInIrEmitter.probe(context)
        builtIn = probe.emitter
        _state.update { it.copy(builtInFeature = probe.featureDeclared, builtInEmitter = probe.hasEmitter) }
        if (probe.emitter != null) _state.update { it.copy(active = probe.emitter) }
        refresh()
    }

    /** Relit la liste des périphériques USB et des sorties audio. */
    fun refresh() {
        val previous = _state.value.usb.associateBy { it.deviceName }
        val toProbe = ArrayList<UsbDevice>()
        val entries = usbManager.deviceList.values.map { device ->
            val driver = prober.probeDevice(device)
            val candidate = when {
                device.vendorId == IrToyEmitter.VENDOR_ID && device.productId == IrToyEmitter.PRODUCT_ID ->
                    IrEmitter.Kind.USB_IR_TOY
                driver != null -> IrEmitter.Kind.USB_BRIDGE
                else -> null
            }
            val old = previous[device.deviceName]
            val status = when {
                candidate == null -> UsbStatus.UNKNOWN_DEVICE
                old != null && old.status != UsbStatus.NEEDS_PERMISSION -> old.status
                !usbManager.hasPermission(device) -> UsbStatus.NEEDS_PERMISSION
                else -> UsbStatus.PROBING.also { toProbe += device }
            }
            UsbEntry(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                description = describe(device),
                candidate = candidate,
                status = status,
                detail = old?.detail?.takeIf { status == old.status },
            )
        }
        val audio = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in AudioIrEmitter.SUPPORTED_TYPES }
            .map { AudioOutput(it.id, it.productName?.toString() ?: "Sortie audio", audioTypeLabel(it.type)) }
        _state.update { it.copy(usb = entries, audioOutputs = audio) }
        // Vérifie automatiquement les périphériques déjà autorisés.
        toProbe.forEach { d -> scope.launch { probeUsb(d) } }
    }

    fun requestPermission(deviceName: String) {
        val device = usbManager.deviceList[deviceName] ?: return
        if (usbManager.hasPermission(device)) {
            scope.launch { probeUsb(device) }
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
        )
        usbManager.requestPermission(device, intent)
    }

    private suspend fun probeUsb(device: UsbDevice) {
        setUsbStatus(device, UsbStatus.PROBING, null)
        val driver: UsbSerialDriver = prober.probeDevice(device) ?: run {
            setUsbStatus(device, UsbStatus.UNKNOWN_DEVICE, "Aucun pilote série USB pour ce périphérique")
            return
        }
        val connection = usbManager.openDevice(device) ?: run {
            setUsbStatus(device, UsbStatus.PERMISSION_DENIED, "Impossible d'ouvrir le périphérique (autorisation ?)")
            return
        }
        val port = driver.ports.first()
        val emitter: IrEmitter = try {
            port.open(connection)
            port.setParameters(115_200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { port.dtr = true; port.rts = true }
            val link = SerialLink(port)
            val label = describe(device)
            if (device.vendorId == IrToyEmitter.VENDOR_ID && device.productId == IrToyEmitter.PRODUCT_ID) {
                IrToyEmitter(link, label).also { it.handshake() }
            } else {
                SerialBridgeEmitter(link, label).also { it.handshake() }
            }
        } catch (e: Exception) {
            runCatching { port.close() }
            setUsbStatus(device, UsbStatus.INCOMPATIBLE, e.message)
            return
        }
        _state.value.active?.takeIf { it.kind != IrEmitter.Kind.BUILT_IN }?.close()
        activeDeviceName = device.deviceName
        setUsbStatus(device, UsbStatus.VERIFIED, emitter.verification)
        _state.update {
            it.copy(active = emitter, message = "Émetteur IR actif : ${emitter.kind.label}" +
                if (emitter.canReceive) " (émission + réception)" else " (émission seule)")
        }
    }

    /** Active explicitement le mode audio : non vérifiable électroniquement. */
    fun activateAudio(outputId: Int): Boolean {
        val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == outputId }
            ?: return false
        _state.value.active?.takeIf { it.kind != IrEmitter.Kind.BUILT_IN }?.close()
        activeDeviceName = null
        val emitter = AudioIrEmitter(device)
        _state.update { it.copy(active = emitter, message = "Mode IR audio activé manuellement — à valider par un essai.") }
        return true
    }

    fun deactivate() {
        val active = _state.value.active ?: return
        if (active.kind == IrEmitter.Kind.BUILT_IN) return
        active.close()
        activeDeviceName = null
        _state.update { it.copy(active = builtIn, message = null) }
    }

    private fun setUsbStatus(device: UsbDevice, status: UsbStatus, detail: String?) {
        _state.update { s ->
            s.copy(usb = s.usb.map { if (it.deviceName == device.deviceName) it.copy(status = status, detail = detail) else it })
        }
    }

    private fun describe(device: UsbDevice): String {
        // Les chaînes USB (fabricant/produit) peuvent exiger l'autorisation : on reste prudent.
        val product = runCatching { device.productName }.getOrNull()
        val maker = runCatching { device.manufacturerName }.getOrNull()
        val known = when {
            device.vendorId == IrToyEmitter.VENDOR_ID && device.productId == IrToyEmitter.PRODUCT_ID ->
                "USB IR Toy / Irdroid (04D8:FD08)"
            else -> null
        }
        return listOfNotNull(known, maker, product).distinct().joinToString(" — ").ifEmpty {
            "Périphérique USB %04X:%04X".format(device.vendorId, device.productId)
        }
    }

    private fun audioTypeLabel(type: Int) = when (type) {
        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "Casque USB"
        android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "Adaptateur audio USB"
        android.media.AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Accessoire USB"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Prise jack (micro)"
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Prise jack"
        else -> "Sortie analogique"
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)

    companion object {
        const val ACTION_USB_PERMISSION = "com.projecteur.remote.USB_PERMISSION"
    }
}
