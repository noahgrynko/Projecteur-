package com.projecteur.remote.data

import android.content.Context

/** Préférences locales (stockage privé de l'application, jamais envoyé ailleurs). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var selectedProfileId: String?
        get() = prefs.getString("ir_profile", null)
        set(v) = prefs.edit().putString("ir_profile", v).apply()

    var pjlinkHost: String
        get() = prefs.getString("pjlink_host", "") ?: ""
        set(v) = prefs.edit().putString("pjlink_host", v).apply()

    /** Mot de passe PJLink du projecteur (fourni par l'utilisateur, conservé seulement s'il le souhaite). */
    var pjlinkPassword: String
        get() = prefs.getString("pjlink_password", "") ?: ""
        set(v) = prefs.edit().putString("pjlink_password", v).apply()

    var rememberPassword: Boolean
        get() = prefs.getBoolean("pjlink_remember", false)
        set(v) = prefs.edit().putBoolean("pjlink_remember", v).apply()

    var lastBluetoothHost: String?
        get() = prefs.getString("bt_host", null)
        set(v) = prefs.edit().putString("bt_host", v).apply()
}
