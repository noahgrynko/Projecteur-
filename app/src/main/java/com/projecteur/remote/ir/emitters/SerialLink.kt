package com.projecteur.remote.ir.emitters

import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Petite couche d'entrées/sorties bloquantes au-dessus d'un port série USB. */
class SerialLink(private val port: UsbSerialPort) {
    private val pending = ArrayDeque<Byte>()
    private val buffer = ByteArray(4096)

    fun write(bytes: ByteArray, timeoutMs: Int = 1000) = port.write(bytes, timeoutMs)

    /** Lit ce qui arrive pendant au plus [timeoutMs] ; renvoie éventuellement 0 octet. */
    fun readSome(timeoutMs: Int): ByteArray {
        if (pending.isNotEmpty()) return ByteArray(pending.size) { pending.removeFirst() }
        val n = try { port.read(buffer, timeoutMs.coerceAtLeast(1)) } catch (e: IOException) {
            throw IrHardwareException("Périphérique USB déconnecté ou illisible : ${e.message}", e)
        }
        return if (n <= 0) ByteArray(0) else buffer.copyOf(n)
    }

    fun unread(bytes: ByteArray) { for (i in bytes.indices.reversed()) pending.addFirst(bytes[i]) }

    fun drain(timeoutMs: Int = 50) {
        pending.clear()
        while (readSome(timeoutMs).isNotEmpty()) Unit
    }

    /** Lit exactement [count] octets, ou lève une exception au bout de [timeoutMs]. */
    fun readExact(count: Int, timeoutMs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (out.size() < count) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) throw IrHardwareException("Pas de réponse du périphérique USB (délai dépassé)")
            val chunk = readSome(remaining.coerceAtMost(200).toInt())
            val take = minOf(chunk.size, count - out.size())
            out.write(chunk, 0, take)
            if (take < chunk.size) unread(chunk.copyOfRange(take, chunk.size))
        }
        return out.toByteArray()
    }

    /** Lit une ligne terminée par '\n' (sans le terminateur), ou null si délai dépassé. */
    fun readLine(timeoutMs: Long, maxLength: Int = 16_384): String? {
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = readSome(100)
            for ((i, b) in chunk.withIndex()) {
                val c = b.toInt().toChar()
                if (c == '\n') {
                    if (i + 1 < chunk.size) unread(chunk.copyOfRange(i + 1, chunk.size))
                    return out.toString().trimEnd('\r')
                }
                out.append(c)
                if (out.length > maxLength) throw IrHardwareException("Réponse USB trop longue")
            }
        }
        return null
    }

    fun close() = runCatching { port.close() }
}
