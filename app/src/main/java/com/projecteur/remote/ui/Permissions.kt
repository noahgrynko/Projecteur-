package com.projecteur.remote.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /** Autorisations Bluetooth selon la version d'Android (Pixel 9a : Android 15+). */
    val bluetooth: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Recherche et connexion suffisent pour utiliser le Bluetooth ; BLUETOOTH_ADVERTISE ne sert
     * qu'à rendre le téléphone visible et son refus ne doit pas bloquer le reste.
     */
    fun hasBluetooth(context: Context) = bluetooth.filterNot { it == Manifest.permission.BLUETOOTH_ADVERTISE }.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

}
