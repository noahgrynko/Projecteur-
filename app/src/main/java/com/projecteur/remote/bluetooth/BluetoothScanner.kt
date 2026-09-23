package com.projecteur.remote.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Recherche d'appareils Bluetooth (classique + BLE) et inspection de leurs services (SDP/GATT).
 * Toutes les méthodes supposent les autorisations BLUETOOTH_SCAN/CONNECT déjà accordées
 * (vérifiées par l'interface avant l'appel).
 */
@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context, private val scope: CoroutineScope) {

    data class Device(
        val address: String,
        val name: String?,
        val bonded: Boolean,
        val type: String,
        val rssi: Int?,
        val deviceClass: Int?,
        val uuids: Set<UUID> = emptySet(),
        val inspected: Boolean = false,
        val inspecting: Boolean = false,
        val inspectError: String? = null,
    ) {
        val displayName get() = name ?: "(sans nom) $address"
        val probableProjector get() = BluetoothServices.looksLikeProjector(name, deviceClass)
    }

    enum class Availability { NO_HARDWARE, DISABLED, READY }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? get() = manager?.adapter

    private val _devices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun availability(): Availability = when {
        adapter == null -> Availability.NO_HARDWARE
        adapter?.isEnabled != true -> Availability.DISABLED
        else -> Availability.READY
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d = intent.device() ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        .takeIf { it != Short.MIN_VALUE.toInt() }
                    upsert(d, rssi)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> Unit
                BluetoothDevice.ACTION_UUID -> {
                    val d = intent.device() ?: return
                    @Suppress("DEPRECATION")
                    val uuids = (if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
                    else intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID))
                        ?.mapNotNull { (it as? ParcelUuid)?.uuid }.orEmpty()
                    _devices.update { m ->
                        val old = m[d.address] ?: return@update m
                        m + (d.address to old.copy(uuids = old.uuids + uuids, inspected = true, inspecting = false))
                    }
                }
            }
        }
    }
    private var registered = false

    private val leCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val uuids = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()
            upsert(result.device, result.rssi, result.scanRecord?.deviceName, uuids.toSet())
        }
        override fun onScanFailed(errorCode: Int) {
            _message.value = "Recherche BLE impossible (code $errorCode)."
        }
    }

    /** Lance une recherche de [durationMs] (classique + BLE) et liste aussi les appareils associés. */
    fun scan(durationMs: Long = 12_000) {
        val a = adapter ?: run { _message.value = "Ce téléphone n'a pas de Bluetooth."; return }
        if (!a.isEnabled) { _message.value = "Bluetooth désactivé : activez-le pour rechercher le projecteur."; return }
        if (!registered) {
            ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_UUID)
            }, ContextCompat.RECEIVER_EXPORTED)
            registered = true
        }
        a.bondedDevices?.forEach { upsert(it, null) }
        _message.value = null
        _scanning.value = true
        runCatching { a.cancelDiscovery(); a.startDiscovery() }
            .onFailure { _message.value = "Recherche classique impossible : ${it.message}" }
        val le = a.bluetoothLeScanner
        runCatching { le?.startScan(leCallback) }
        scope.launch {
            delay(durationMs)
            runCatching { a.cancelDiscovery() }
            runCatching { le?.stopScan(leCallback) }
            _scanning.value = false
            if (_devices.value.isEmpty()) _message.value = "Aucun appareil Bluetooth trouvé à proximité."
        }
    }

    private fun upsert(d: BluetoothDevice, rssi: Int?, advName: String? = null, advUuids: Set<UUID> = emptySet()) {
        val name = runCatching { d.name }.getOrNull() ?: advName
        val cls: BluetoothClass? = runCatching { d.bluetoothClass }.getOrNull()
        val type = when (runCatching { d.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classique"
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "Classique + BLE"
            else -> "Inconnu"
        }
        val cached = runCatching { d.uuids?.map { it.uuid } }.getOrNull().orEmpty()
        _devices.update { m ->
            val old = m[d.address]
            m + (d.address to Device(
                address = d.address,
                name = name ?: old?.name,
                bonded = d.bondState == BluetoothDevice.BOND_BONDED,
                type = type,
                rssi = rssi ?: old?.rssi,
                deviceClass = cls?.deviceClass ?: old?.deviceClass,
                uuids = (old?.uuids.orEmpty()) + cached + advUuids,
                inspected = old?.inspected ?: false,
                inspecting = old?.inspecting ?: false,
            ))
        }
    }

    /** Interroge les services : SDP pour le Bluetooth classique, GATT pour le BLE. */
    fun inspect(address: String) {
        val a = adapter ?: return
        val device = runCatching { a.getRemoteDevice(address) }.getOrNull() ?: return
        _devices.update { m -> m[address]?.let { m + (address to it.copy(inspecting = true, inspectError = null)) } ?: m }
        runCatching { a.cancelDiscovery() }
        val isLe = device.type == BluetoothDevice.DEVICE_TYPE_LE
        if (!isLe) {
            if (!device.fetchUuidsWithSdp()) markInspectError(address, "Requête SDP refusée")
            scope.launch {
                delay(15_000)
                if (_devices.value[address]?.inspecting == true) markInspectError(address, "Pas de réponse SDP (appareil hors de portée ou éteint ?)")
            }
        } else {
            device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices()
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gatt.close()
                        if (_devices.value[address]?.inspecting == true) markInspectError(address, "Connexion GATT impossible (code $status)")
                    }
                }
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val uuids = gatt.services.map { it.uuid }
                    _devices.update { m ->
                        val old = m[address] ?: return@update m
                        m + (address to old.copy(uuids = old.uuids + uuids, inspected = true, inspecting = false))
                    }
                    gatt.disconnect()
                }
            }, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private fun markInspectError(address: String, msg: String) {
        _devices.update { m -> m[address]?.let { m + (address to it.copy(inspecting = false, inspectError = msg)) } ?: m }
    }

    fun pair(address: String): Boolean {
        val d = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return false
        return d.createBond()
    }

    @Suppress("DEPRECATION")
    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
}
