package com.biotrack.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class InsightsPanelTest {
    private fun nodes(v: View): List<View> = listOf(v)+(if(v is ViewGroup) (0 until v.childCount).flatMap { nodes(v.getChildAt(it)) } else emptyList())
    @Test fun reportSectionsAndExportsUseTheSelectedSnapshot() {
        val instrumentation=InstrumentationRegistry.getInstrumentation(); val context=instrumentation.targetContext
        val id=UUID.randomUUID().toString(); val name="insights-$id.db"; val prefs=context.getSharedPreferences("test-$id",0)
        val store=ReadingStore(context,name)
        // Complete prior calendar days keep this fixture independent of the emulator clock.
        val today=LocalDate.now().minusDays(1)
        prefs.edit().putString("report_start",today.minusDays(13).toString()).putString("report_end",today.toString()).commit()
        repeat(14) { day -> repeat(3) { index ->
            val date=today.minusDays(day.toLong()); val sequence=day*3+index
            val bytes=byteArrayOf(6,sequence.toByte(),0,(date.year and 255).toByte(),(date.year ushr 8).toByte(),date.monthValue.toByte(),date.dayOfMonth.toByte(),(7+index*6).toByte(),15,0,(50+day+index*10).toByte(),0xc0.toByte(),0x11)
            store.save("Synthetic UI meter",GlucoseProtocol.measurement(bytes))
        } }
        var activity: MainActivity?=null
        var pdfSnapshot: GlucoseReport?=null; var csvSnapshot: GlucoseReport?=null
        try {
            activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val screen=activity
            instrumentation.runOnMainSync {
                screen.setContentView(ScrollView(screen).apply { addView(InsightsPanel(screen,store,prefs,{}, { pdfSnapshot=it }, { csvSnapshot=it })) })
            }
            instrumentation.waitForIdleSync()
            fun capture(label: String) {
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,"BioTrack-0.3.0-$label.png"); put(MediaStore.MediaColumns.MIME_TYPE,"image/png"); put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/BioTrackTesting")
                })!!
                context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
            capture("summary")
            instrumentation.runOnMainSync { nodes(screen.window.decorView).filterIsInstance<Spinner>().first { it.contentDescription=="Analysis section" }.setSelection(2) }
            instrumentation.waitForIdleSync(); capture("overlay")
            instrumentation.runOnMainSync {
                assertTrue(nodes(screen.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="24-hour overlay" })
                nodes(screen.window.decorView).filterIsInstance<Spinner>().first { it.contentDescription=="Analysis section" }.setSelection(4)
            }
            instrumentation.waitForIdleSync(); capture("reports")
            instrumentation.runOnMainSync {
                val views=nodes(screen.window.decorView).filterIsInstance<Button>()
                views.first { it.text.toString()=="Save detailed PDF report" }.performClick()
                views.first { it.text.toString()=="Export filtered readings as CSV" }.performClick()
            }
            assertNotNull(pdfSnapshot); assertSame(pdfSnapshot,csvSnapshot); assertEquals(42,pdfSnapshot!!.rows.size)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }; store.close(); context.deleteDatabase(name); prefs.edit().clear().commit()
        }
    }
}
