package com.projecteur.remote.bluetooth

import com.projecteur.remote.core.RemoteCommand

/**
 * Descripteur HID standard (clavier + « Consumer Control ») utilisé quand le téléphone se
 * présente comme une télécommande Bluetooth. Les usages proviennent des tables HID Usage de
 * l'USB-IF ; Android/Linux les traduit en touches (DPAD, ENTER, BACK, VOLUME…).
 */
object HidReports {
    const val KEYBOARD_ID = 1
    const val CONSUMER_ID = 2

    val DESCRIPTOR: ByteArray = intArrayOf(
        // Clavier, rapport 1 : modificateurs, octet réservé, 6 touches
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD_ID,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x26, 0xFF, 0x00, 0x05, 0x07, 0x19, 0x00, 0x2A, 0xFF, 0x00, 0x81, 0x00,
        0xC0,
        // Consumer Control, rapport 2 : un usage 16 bits
        0x05, 0x0C, 0x09, 0x01, 0xA1, 0x01, 0x85, CONSUMER_ID,
        0x15, 0x00, 0x26, 0xFF, 0x03, 0x19, 0x00, 0x2A, 0xFF, 0x03, 0x75, 0x10, 0x95, 0x01, 0x81, 0x00,
        0xC0,
    ).map { it.toByte() }.toByteArray()

    sealed class Usage {
        data class Key(val code: Int) : Usage()
        data class Consumer(val code: Int) : Usage()
    }

    /** Seules les commandes ayant un équivalent HID standard sont proposées. */
    val mapping: Map<RemoteCommand, Usage> = mapOf(
        RemoteCommand.POWER to Usage.Consumer(0x30),
        RemoteCommand.VOL_UP to Usage.Consumer(0xE9),
        RemoteCommand.VOL_DOWN to Usage.Consumer(0xEA),
        RemoteCommand.MUTE to Usage.Consumer(0xE2),
        RemoteCommand.MENU to Usage.Consumer(0x40),
        RemoteCommand.BACK to Usage.Consumer(0x224),
        RemoteCommand.UP to Usage.Key(0x52),
        RemoteCommand.DOWN to Usage.Key(0x51),
        RemoteCommand.LEFT to Usage.Key(0x50),
        RemoteCommand.RIGHT to Usage.Key(0x4F),
        RemoteCommand.OK to Usage.Key(0x28),
        RemoteCommand.EXIT to Usage.Key(0x29),
    )

    fun press(usage: Usage): Pair<Int, ByteArray> = when (usage) {
        is Usage.Key -> KEYBOARD_ID to byteArrayOf(0, 0, usage.code.toByte(), 0, 0, 0, 0, 0)
        is Usage.Consumer -> CONSUMER_ID to byteArrayOf((usage.code and 0xFF).toByte(), (usage.code shr 8).toByte())
    }

    fun release(usage: Usage): Pair<Int, ByteArray> = when (usage) {
        is Usage.Key -> KEYBOARD_ID to ByteArray(8)
        is Usage.Consumer -> CONSUMER_ID to ByteArray(2)
    }
}
