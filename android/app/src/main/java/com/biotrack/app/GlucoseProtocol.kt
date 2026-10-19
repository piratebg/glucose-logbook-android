package com.biotrack.app

import java.time.LocalDateTime
import kotlin.math.pow

data class MeterReading(val sequence: Int, val localTime: LocalDateTime, val timeOffset: Int?,
    val mmol: Double?, val originalValue: Double?, val originalUnit: String?, val sampleType: Int?,
    val status: Int?, val contextFollows: Boolean, val raw: ByteArray) {
    val eligible get() = mmol != null && mmol > 0 && sampleType != 10 && (status ?: 0) == 0
}

object GlucoseProtocol {
    const val MG_PER_MMOL = 18.01559
    fun sfloat(raw: Int): Double? {
        if (raw in setOf(0x7FE, 0x7FF, 0x800, 0x801, 0x802)) return null
        var mantissa = raw and 0xFFF
        if (mantissa >= 0x800) mantissa -= 0x1000
        var exponent = raw ushr 12 and 15
        if (exponent >= 8) exponent -= 16
        return mantissa * 10.0.pow(exponent)
    }
    fun measurement(bytes: ByteArray): MeterReading {
        var p = 0
        fun u8(): Int { require(p < bytes.size) { "Truncated glucose packet" }; return bytes[p++].toInt() and 255 }
        fun u16(): Int = u8() or (u8() shl 8)
        val flags = u8()
        require(flags and 0xE0 == 0) { "Unknown glucose flags" }
        val sequence = u16()
        val date = LocalDateTime.of(u16(), u8(), u8(), u8(), u8(), u8())
        val offset = if (flags and 1 != 0) u16().toShort().toInt() else null
        var value: Double? = null
        var mmol: Double? = null
        var type: Int? = null
        val mol = flags and 4 != 0
        if (flags and 2 != 0) {
            value = sfloat(u16())
            type = u8() and 15
            // SIG encodes kg/L or mol/L, not the consumer display units.
            mmol = value?.let { if (mol) it * 1000.0 else it * 100000.0 / MG_PER_MMOL }
        }
        val status = if (flags and 8 != 0) u16() else null
        require(p == bytes.size) { "Unexpected glucose payload length" }
        return MeterReading(sequence, date, offset, mmol,
            value?.let { if (mol) it * 1000.0 else it * 100000.0 },
            if (value == null) null else if (mol) "mmol/L" else "mg/dL", type, status, flags and 16 != 0, bytes.copyOf())
    }
    fun requestSince(sequence: Int?): ByteArray = if (sequence == null || sequence >= 65534)
        byteArrayOf(1, 1) else byteArrayOf(1, 3, 1, (sequence and 255).toByte(), (sequence ushr 8).toByte())
}
