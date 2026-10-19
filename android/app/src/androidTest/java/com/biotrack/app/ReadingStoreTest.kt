package com.biotrack.app

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReadingStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-${UUID.randomUUID()}.db"
    private lateinit var store: ReadingStore
    @Before fun start() { store=ReadingStore(context,name) }
    @After fun stop() { store.close(); context.deleteDatabase(name) }
    private fun packet(seq: Int=42) = GlucoseProtocol.measurement(byteArrayOf(2,seq.toByte(),(seq ushr 8).toByte(),0xea.toByte(),7,10,19,12,34,56,0x64,0xb0.toByte(),0x11))
    @Test fun repeatedHistoryIsIdempotentAfterRestart() {
        assertTrue(store.save("meter-A",packet())); assertFalse(store.save("meter-A",packet()))
        store.close(); store=ReadingStore(context,name)
        assertFalse(store.save("meter-A",packet())); assertEquals(1,store.readings().size)
    }
    @Test fun differentMetersDoNotCollide() {
        store.save("meter-A",packet()); store.save("meter-B",packet()); assertEquals(2,store.readings().size)
    }
    @Test fun lateReadingsRemainDistinctAndDurable() {
        store.save("meter-A",packet(45)); store.save("meter-A",packet(40))
        store.close(); store=ReadingStore(context,name); assertEquals(2,store.readings().size)
    }
    @Test fun persistenceDoesNotAdvanceCheckpointUntilProcedureCompletes() {
        store.save("meter-A",packet()); assertNull(store.checkpoint("meter-A"))
        store.complete("meter-A",42); store.close(); store=ReadingStore(context,name)
        assertEquals(42,store.checkpoint("meter-A"))
    }
    @Test fun timeReviewAndExportReceiptSurviveRestart() {
        store.save("meter-A",packet()); val r=store.readings().single()
        assertFalse(r.timeConfirmed); assertFalse(r.exported)
        store.confirm(r.id); store.exported(r.id); store.close(); store=ReadingStore(context,name)
        assertTrue(store.readings().single().timeConfirmed); assertTrue(store.readings().single().exported)
        assertEquals(r.mmol,store.readings().single().mmol,0.0)
    }
    @Test fun annotationsPreserveSyncIdentityValuesAndExportReceipt() {
        store.save("meter-A",packet()); val original=store.readings().single()
        store.confirm(original.id); store.exported(original.id); store.complete("meter-A",42)
        store.annotate(original.id,"Fasting","Breakfast context")
        store.close(); store=ReadingStore(context,name)
        val updated=store.readings().single()
        assertEquals(original.id,updated.id); assertEquals(original.mmol,updated.mmol,0.0); assertEquals(original.time,updated.time)
        assertTrue(updated.exported); assertTrue(updated.timeConfirmed); assertEquals(42,store.checkpoint("meter-A"))
        assertEquals("Fasting",updated.mealTag); assertEquals("Breakfast context",updated.userNote)
        assertFalse(store.save("meter-A",packet()))
    }
    @Test fun upgradesVersionOneWithoutLosingReadingsOrReceipts() {
        context.openOrCreateDatabase(name,0,null).use { db ->
            db.execSQL("CREATE TABLE readings(id TEXT PRIMARY KEY, mmol REAL, time INTEGER NOT NULL, source TEXT NOT NULL, note TEXT NOT NULL, exported INTEGER NOT NULL DEFAULT 0, eligible INTEGER NOT NULL, confirmed INTEGER NOT NULL, raw BLOB, local_time TEXT, sequence INTEGER, original_value REAL, original_unit TEXT, status INTEGER)")
            db.execSQL("CREATE TABLE checkpoints(device TEXT PRIMARY KEY,sequence INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE contexts(device TEXT NOT NULL, sequence INTEGER NOT NULL,payload BLOB NOT NULL,received INTEGER NOT NULL,PRIMARY KEY(device,sequence,payload))")
            db.execSQL("INSERT INTO readings(id,mmol,time,source,note,exported,eligible,confirmed) VALUES('old',5.6,123456,'Manual','Fasting · Existing note',1,1,1)")
            db.execSQL("INSERT INTO checkpoints VALUES('meter-A',42)")
            db.version=1
        }
        val row=store.readings().single()
        assertEquals("old",row.id); assertEquals(5.6,row.mmol,0.0); assertEquals(123456L,row.time)
        assertTrue(row.exported && row.timeConfirmed); assertEquals("Fasting",row.mealTag); assertEquals("Existing note",row.userNote)
        assertEquals(42,store.checkpoint("meter-A"))
    }
}
