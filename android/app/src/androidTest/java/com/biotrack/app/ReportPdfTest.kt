package com.biotrack.app

import android.content.ContentValues
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class ReportPdfTest {
    @Test fun generatesPaginatedReviewReportFromSharedAnalysisSnapshot() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val zone=ZoneId.of("Asia/Kuala_Lumpur")
        val config=ReportConfig(LocalDate.parse("2026-10-06"),LocalDate.parse("2026-10-19"),zone,3.9,10.0,true)
        val rows=(0..27).flatMap { day -> listOf(7,12,19).map { hour ->
            val time=LocalDate.parse("2026-09-22").plusDays(day.toLong()).atTime(hour,15).atZone(zone).toInstant().toEpochMilli()
            val value=if(day%7==0 && hour==7) 3.1 else if(day%3==0 && hour==19) 12.2 else 5.2+(day%5)*0.6+if(hour==12) 1.8 else 0.0
            Reading("demo-$day-$hour",value,time,"Demo meter","",day<20,true,true,if(hour==7) "Fasting" else if(hour==12) "After meal" else "Before meal",
                if(day==27 && hour==19) "SYNTHETIC TEST DATA. "+"Long note to check wrapping and pagination. ".repeat(35) else "Synthetic test meal context.")
        } }
        val report=GlucoseAnalysis.build(rows,config,LocalDate.parse("2026-10-20").atStartOfDay(zone).toInstant().toEpochMilli())
        assertEquals(42,report.rows.size); assertEquals(42,report.previous.size)
        val file=File(context.cacheDir,"report-preview.pdf")
        file.outputStream().use { ReportPdf.write(report,it,true) }
        assertTrue(file.length()>10000)
        PdfRenderer(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount>=4)
            repeat(renderer.pageCount) { index -> renderer.openPage(index).use { page -> assertEquals(595,page.width); assertEquals(842,page.height) } }
        }
        // Keep only synthetic QA output in the emulator's shared Downloads after test uninstall.
        val uri=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"BioTrack-0.3.0-sample.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE,"application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/BioTrackTesting")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        file.delete()
    }
}
