package com.biotrack.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeterScrollTest {
    private fun nodes(view: View): List<View> = listOf(view) +
        if(view is ViewGroup) (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()

    @Test fun liveScanUpdatesKeepScrollAndSelectableRowsStable() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as BioApp
        val original=app.meter.devices.toMap()
        var activity: MainActivity?=null
        try {
            instrumentation.runOnMainSync {
                app.meter.devices.clear()
                repeat(20) { app.meter.devices["00:11:22:33:44:%02X".format(it)]="Test meter $it" }
            }
            activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val screen=activity
            instrumentation.runOnMainSync {
                nodes(screen.window.decorView).filterIsInstance<TextView>().first { it.text.toString()=="Meter" }.performClick()
            }
            instrumentation.waitForIdleSync()
            lateinit var scroll: ScrollView
            lateinit var row: Button
            var position=0
            instrumentation.runOnMainSync {
                scroll=nodes(screen.window.decorView).filterIsInstance<ScrollView>().single()
                row=nodes(scroll).filterIsInstance<Button>().first { it.text.startsWith("Test meter 10 ·") }
                scroll.scrollTo(0,1400)
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { position=scroll.scrollY; assertTrue(position>0) }
            repeat(4) {
                instrumentation.runOnMainSync { app.meter.onChange?.invoke() }
                Thread.sleep(550)
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertSame("Scan callbacks must not replace the scroll view",scroll,nodes(screen.window.decorView).filterIsInstance<ScrollView>().single())
                    assertEquals("Scan callbacks must not reset scroll",position,scroll.scrollY)
                    assertSame(row,nodes(scroll).filterIsInstance<Button>().first { it.text.startsWith("Test meter 10 ·") })
                }
            }
            instrumentation.runOnMainSync {
                app.meter.devices["00:11:22:33:44:FE"]="Newly discovered meter"
                app.meter.onChange?.invoke()
            }
            Thread.sleep(550); instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertSame(scroll,nodes(screen.window.decorView).filterIsInstance<ScrollView>().single())
                assertEquals(position,scroll.scrollY)
                assertTrue(row.isEnabled && row.hasOnClickListeners())
                assertTrue(nodes(scroll).filterIsInstance<Button>().any { it.text.startsWith("Newly discovered meter ·") })
            }
        } finally {
            instrumentation.runOnMainSync {
                activity?.finish()
                app.meter.devices.clear(); app.meter.devices.putAll(original)
            }
        }
    }
}
