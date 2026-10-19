package com.biotrack.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class GlucoseProtocolTest {
    private fun packet(flags: Int=2, suffix: ByteArray=byteArrayOf(0x64,0xb0.toByte(),0x11)) =
        byteArrayOf(flags.toByte(),42,0,0xea.toByte(),7,10,19,12,34,56)+suffix
    @Test fun decodesMgDlWithoutConfusingKgPerL() {
        val r=GlucoseProtocol.measurement(packet())
        assertEquals(100.0,r.originalValue!!,0.0001)
        assertEquals(100/18.01559,r.mmol!!,0.00001)
        assertEquals("mg/dL",r.originalUnit); assertEquals(42,r.sequence)
        assertEquals(LocalDateTime.of(2026,10,19,12,34,56),r.localTime)
    }
    @Test fun decodesMolPerL() {
        val r=GlucoseProtocol.measurement(packet(6,byteArrayOf(55,0xc0.toByte(),0x11)))
        assertEquals(5.5,r.mmol!!,0.000001); assertEquals("mmol/L",r.originalUnit)
    }
    @Test fun supportsSignedOffsetAndContextFlag() {
        val r=GlucoseProtocol.measurement(packet(19,byteArrayOf(0xc4.toByte(),0xff.toByte(),0x64,0xb0.toByte(),0x11)))
        assertEquals(-60,r.timeOffset); assertTrue(r.contextFollows)
    }
    @Test fun missingConcentrationIsNotZero() {
        val r=GlucoseProtocol.measurement(packet(0,byteArrayOf()))
        assertNull(r.mmol); assertFalse(r.eligible)
    }
    @Test fun excludesControlSolution() {
        assertFalse(GlucoseProtocol.measurement(packet(2,byteArrayOf(0x64,0xb0.toByte(),0x1a))).eligible)
    }
    @Test fun excludesFlaggedMeasurements() {
        assertFalse(GlucoseProtocol.measurement(packet(10,byteArrayOf(0x64,0xb0.toByte(),0x11,1,0))).eligible)
    }
    @Test fun specialValuesRemainMissing() {
        for(raw in listOf(0x7fe,0x7ff,0x800,0x801,0x802)) assertNull(GlucoseProtocol.sfloat(raw))
    }
    @Test fun negativeSfloatAndExponent() {
        assertEquals(-0.1,GlucoseProtocol.sfloat(0xffff)!!,0.00001)
        assertEquals(204.7,GlucoseProtocol.sfloat(0xf7ff)!!,0.00001)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsTruncatedPacket() { GlucoseProtocol.measurement(packet().dropLast(1).toByteArray()) }
    @Test(expected=java.time.DateTimeException::class) fun rejectsUnknownDate() { val b=packet(); b[5]=0; GlucoseProtocol.measurement(b) }
    @Test(expected=IllegalArgumentException::class) fun rejectsTrailingBytes() { GlucoseProtocol.measurement(packet()+byteArrayOf(0)) }
    @Test(expected=IllegalArgumentException::class) fun rejectsUnknownFlags() { GlucoseProtocol.measurement(packet(0x82)) }
    @Test fun incrementalRequestUsesLittleEndianSequence() {
        assertArrayEquals(byteArrayOf(1,3,1,0x34,0x12),GlucoseProtocol.requestSince(0x1234))
    }
    @Test fun wrapAndFirstSyncUseFullRecovery() {
        assertArrayEquals(byteArrayOf(1,1),GlucoseProtocol.requestSince(null))
        assertArrayEquals(byteArrayOf(1,1),GlucoseProtocol.requestSince(65535))
    }
}
