package com.biotrack.app

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class ReportExportState : androidx.lifecycle.ViewModel() {
    var pdf: GlucoseReport?=null
    var csv: GlucoseReport?=null
}

class MainActivity : ComponentActivity() {
    private val bio get() = application as BioApp
    private val prefs by lazy { getSharedPreferences("display",0) }
    private val exporter by lazy { HealthExporter(this,bio.store) }
    private val ink=Color.rgb(24,48,45); private val teal=Color.rgb(8,127,114); private val muted=Color.rgb(87,108,104)
    private lateinit var content: LinearLayout
    private lateinit var root: LinearLayout
    private lateinit var pageScroll: ScrollView
    private var renderedPage: String?=null
    private var meterStatus: TextView?=null
    private var meterDeviceList: LinearLayout?=null
    private val meterDeviceButtons=linkedMapOf<String,Button>()
    private val meterActionButtons=mutableListOf<Pair<Button,()->Boolean>>()
    private var page="Today"
    private var shareBusy=false
    private val reportExportState by viewModels<ReportExportState>()
    private val uiHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private var refreshPosted=false
    private val refresh=Runnable {
        refreshPosted=false
        if(page=="Meter" && meterDeviceList!=null) updateMeter() else render()
    }
    private var pendingAction: (() -> Unit)?=null
    private val notificationPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { render() }
    private val mmol get()=prefs.getBoolean("mmol",true)
    private val unit get()=if(mmol) "mmol/L" else "mg/dL"
    private val low get()=prefs.getFloat("low",3.9f).toString().toDouble()
    private val high get()=prefs.getFloat("high",10f).toString().toDouble()
    private fun value(v: Double)=String.format(Locale.getDefault(),if(mmol) "%.1f" else "%.0f",if(mmol) v else v*GlucoseProtocol.MG_PER_MMOL)
    private fun date(time: Long)=Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm"))
    private val bluetoothPermission=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if(bio.meter.permissions()) pendingAction?.invoke() else message("Nearby devices permission is needed to connect your meter.")
        pendingAction=null
    }
    private val healthPermission=registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        message(if(granted.containsAll(HealthExporter.permissions)) "Health Connect is ready. Tap Share pending readings when you choose." else "Permission was not granted. Your readings remain in Bio Track.")
        render()
    }
    private val reportPdf=registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot=reportExportState.pdf; reportExportState.pdf=null
        if(uri!=null && snapshot==null) message("The report selection expired. Open Reports and save again.")
        if(uri!=null && snapshot!=null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { ReportPdf.write(snapshot,it) } ?: error("Cannot open report destination")
            } }.onSuccess { message("Detailed PDF report saved") }.onFailure { message("Report failed: ${it.localizedMessage}") }
        }
    }
    private val reportCsv=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val snapshot=reportExportState.csv; reportExportState.csv=null
        if(uri!=null && snapshot==null) message("The report selection expired. Open Reports and export again.")
        if(uri!=null && snapshot!=null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(GlucoseAnalysis.csv(snapshot)) } ?: error("Cannot open report destination")
            } }.onSuccess { message("Filtered CSV saved") }.onFailure { message("Export failed: ${it.localizedMessage}") }
        }
    }
    private val csv=registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if(uri!=null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                writer.write("id,time,glucose_mmol_l,glucose_mg_dl,source,note,shared,time_confirmed,meal_tag,user_note\n")
                fun quote(s: String)="\""+s.replace("\"","\"\"")+"\""
                bio.store.readings().forEach { r -> writer.write(listOf(r.id,Instant.ofEpochMilli(r.time).toString(),r.mmol.toString(),(r.mmol*GlucoseProtocol.MG_PER_MMOL).toString(),r.source,r.note,r.exported.toString(),r.timeConfirmed.toString(),r.mealTag,r.userNote).joinToString(",",transform=::quote)+"\n") }
            } ?: error("Cannot open export destination") } }.onSuccess { message("CSV saved") }.onFailure { message("Export failed: ${it.localizedMessage}") }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.action=="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE") { startActivity(Intent(this,PrivacyActivity::class.java)); finish(); return }
        page=savedInstanceState?.getString("page") ?: "Today"
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(244,248,246)) }
        root.setOnApplyWindowInsetsListener { v,insets ->
            @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root); render()
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("page",page); super.onSaveInstanceState(out) }
    override fun onStart() { super.onStart(); bio.meter.onChange={
        if(!refreshPosted) { refreshPosted=true; uiHandler.postDelayed(refresh,400) }
    }; if(::root.isInitialized) render() }
    override fun onStop() { bio.meter.onChange=null; uiHandler.removeCallbacks(refresh); refreshPosted=false; super.onStop() }
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(s: String,size: Float=16f,color: Int=ink,bold: Boolean=false)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color); if(bold) setTypeface(typeface,Typeface.BOLD); setPadding(0,dp(4),0,dp(4))
    }
    private fun rounded(color: Int)=GradientDrawable().apply { setColor(color); cornerRadius=dp(22).toFloat() }
    private fun card(title: String,description: String?=null): LinearLayout {
        val c=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(Color.WHITE); setPadding(dp(20),dp(16),dp(20),dp(16)) }
        content.addView(c,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(14) })
        c.addView(text(title,20f,ink,true)); if(description!=null) c.addView(text(description,14f,muted)); return c
    }
    private fun button(parent: LinearLayout,label: String,enabled: Boolean=true,action: () -> Unit): Button {
        val view=Button(this).apply { text=label; isAllCaps=false; textSize=15f; minHeight=dp(52); isEnabled=enabled
            setTextColor(Color.WHITE); backgroundTintList=android.content.res.ColorStateList.valueOf(if(enabled) teal else Color.GRAY)
            setOnClickListener { action() }
        }
        parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        return view
    }
    private fun go(target: String) { page=target; render() }
    private fun render() {
        if(!::root.isInitialized) return
        val previousScroll=if(renderedPage==page && ::pageScroll.isInitialized) pageScroll.scrollY else 0
        renderedPage=page
        meterStatus=null; meterDeviceList=null; meterDeviceButtons.clear(); meterActionButtons.clear()
        root.removeAllViews()
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(20),dp(12),dp(12),dp(8)) }
        header.addView(text("Bio Track",26f,ink,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply { text=unit; isAllCaps=false; contentDescription="Settings, current unit $unit"; setOnClickListener { settings() } })
        root.addView(header)
        val scroll=ScrollView(this)
        pageScroll=scroll
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(24)) }
        scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(dp(6),dp(4),dp(6),dp(4)); setBackgroundColor(Color.WHITE) }
        for(label in listOf("Today","History","Insights","Meter","Share")) nav.addView(TextView(this).apply {
            text=label; textSize=12f; gravity=Gravity.CENTER; minHeight=dp(56); setTextColor(if(page==label) teal else muted)
            if(page==label) setTypeface(typeface,Typeface.BOLD); isSelected=page==label; setOnClickListener { go(label) }; contentDescription="$label tab"
        },LinearLayout.LayoutParams(0,-2,1f))
        root.addView(nav)
        when(page) { "Today" -> today(); "History" -> history(); "Insights" -> insights(); "Meter" -> meter(); "Share" -> share() }
        if(previousScroll>0) scroll.post { scroll.scrollTo(0,previousScroll) }
    }
    private fun today() {
        content.addView(text("Your glucose, at a glance",28f,ink,true))
        content.addView(text("One place for your readings and daily patterns.",15f,muted))
        val rows=bio.store.readings().filter { it.eligible }
        val latest=rows.firstOrNull()
        card("Latest reading").apply {
            addView(text(if(latest==null) "Ready for your first reading" else "${value(latest.mmol)} $unit",if(latest==null) 24f else 38f,teal,true))
            addView(text(latest?.let { "${date(it.time)} · ${if(it.source=="Manual") "Manual entry" else "Meter"}" } ?: "Connect your Accu-Chek Instant to import its stored readings.",14f,muted))
            if(latest!=null && !latest.timeConfirmed) addView(text("Meter time needs review before sharing",14f,muted))
            button(this,if(bio.meter.connected) "Check for new readings" else "Connect your meter",!bio.meter.busy) { if(bio.meter.connected) bio.meter.history(false) else go("Meter") }
        }
        card("Meter status",bio.meter.status)
        val day=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today=rows.filter { it.time>=day }
        card("Today’s summary",if(today.isEmpty()) "No readings recorded today yet." else "${today.size} readings · Average ${value(today.map { it.mmol }.average())} $unit")
        card("Add context", "Record a manual glucose reading with a meal tag or note.").apply { button(this,"Add a reading") { manual() } }
        if(rows.isNotEmpty()) { content.addView(text("Recent readings",20f,ink,true)); rows.take(3).forEach(::readingCard) }
    }
    private fun history() {
        content.addView(text("Your history",28f,ink,true))
        content.addView(text("Readings stay on this phone until you choose to share.",15f,muted))
        val rows=bio.store.readings()
        if(rows.isEmpty()) card("No readings yet","Import from your meter or add your first manual reading.")
        else rows.take(300).forEach(::readingCard)
        if(rows.size>300) content.addView(text("Showing the latest 300 readings. CSV export includes all readings.",14f,muted))
        button(content,"Add a reading") { manual() }
        button(content,"Export all as CSV") { csv.launch("BioTrack-${LocalDate.now()}.csv") }
    }
    private fun readingCard(r: Reading) {
        val label=if(!r.eligible) "Excluded · meter flag" else if(r.mmol<low) "Below target" else if(r.mmol>high) "Above target" else "Within target"
        card("${value(r.mmol)} $unit", "${date(r.time)} · $label").apply {
            addView(text("${if(r.source=="Manual") "Manual entry" else "Meter reading"} · ${if(r.exported) "Shared" else "Stored locally"}",13f,muted))
            if(r.source!="Manual" && r.note.isNotBlank() && (!r.timeConfirmed || !r.eligible)) addView(text(r.note,14f,muted))
            if(r.mealTag.isNotEmpty()) addView(text("Meal context: ${r.mealTag}",14f,teal))
            if(r.userNote.isNotBlank()) addView(text(r.userNote,14f,muted))
            button(this,"Add or edit meal context") { annotate(r) }
            if(r.eligible && !r.timeConfirmed) button(this,"Review meter time") {
                AlertDialog.Builder(this@MainActivity).setTitle("Is this the correct reading time?")
                    .setMessage("${date(r.time)}\n\nThe meter has no timezone. Bio Track interpreted its date and time in ${ZoneId.systemDefault()}. Confirm only if this matches when you measured. If it is wrong, correct the meter clock before taking future readings; keep this record local.")
                    .setNegativeButton("Keep local",null).setPositiveButton("Time is correct") { _,_ -> bio.store.confirm(r.id); render() }.show()
            }
        }
    }
    private fun insights() {
        content.addView(InsightsPanel(this,bio.store,prefs,::annotate,
            { report -> reportExportState.pdf=report; reportPdf.launch("BioTrack-${report.config.start}-${report.config.end}.pdf") },
            { report -> reportExportState.csv=report; reportCsv.launch("BioTrack-${report.config.start}-${report.config.end}.csv") }))
    }
    private fun annotate(r: Reading) {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        form.addView(text("${value(r.mmol)} $unit · ${date(r.time)}",18f,ink,true))
        form.addView(text("Add context without changing the meter value, time or sharing receipt.",14f,muted))
        val options=listOf("No meal tag")+GlucoseAnalysis.MEAL_TAGS
        val tag=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,options); setSelection(options.indexOf(r.mealTag).coerceAtLeast(0)); minimumHeight=dp(48) }
        form.addView(tag)
        val note=EditText(this).apply { hint="Meal, activity, medication or other context"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; setText(r.userNote); filters=arrayOf(android.text.InputFilter.LengthFilter(2000)) }
        form.addView(note)
        AlertDialog.Builder(this).setTitle("Reading context").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
            bio.store.annotate(r.id,if(tag.selectedItemPosition==0) "" else tag.selectedItem.toString(),note.text.toString().trim()); render()
        }.show()
    }
    private fun bluetooth(action: () -> Unit) {
        if(bio.meter.permissions()) action() else {
            pendingAction=action
            bluetoothPermission.launch(if(Build.VERSION.SDK_INT>=31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }
    private fun startSession(address: String?=null) { bluetooth {
        try {
            startForegroundService(Intent(this,MeterService::class.java))
            if(address==null) bio.meter.reconnect() else bio.meter.connect(address)
        } catch (_: Exception) { message("Could not start meter session. Keep Bio Track open and check Bluetooth permissions.") }
    } }
    private fun meter() {
        content.addView(text("Connect your meter",28f,ink,true))
        card("Accu-Chek Instant", "Keep the meter nearby and awake. For first pairing, enable its Bluetooth pairing mode and confirm the phone’s pairing request. If another app is using the meter, close its connection first.")
        card("Connection").apply {
            meterStatus=text(bio.meter.status,14f,muted).apply { minLines=3 }
            addView(meterStatus)
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                button(this,"Enable meter notification") { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            fun action(label: String,enabled: ()->Boolean,run: ()->Unit) {
                meterActionButtons.add(button(this,label,enabled(),run) to enabled)
            }
            action("Search for a meter",{ !bio.meter.busy && !bio.meter.connected && !bio.meter.scanning }) { bluetooth { bio.meter.scan() } }
            action("Reconnect saved meter",{ !bio.meter.busy && !bio.meter.connected && !bio.meter.scanning }) { startSession() }
            action("Check for new readings",{ bio.meter.connected && !bio.meter.busy }) { bio.meter.history(false) }
            action("Recover full meter history",{ bio.meter.connected && !bio.meter.busy }) { bio.meter.history(true) }
            button(this,"Disconnect") { bio.meter.disconnect(); stopService(Intent(this@MainActivity,MeterService::class.java)); updateMeter() }
        }
        content.addView(text("Nearby devices",20f,ink,true))
        content.addView(text("Only select your own glucose meter. Other Bluetooth devices may appear.",14f,muted))
        meterDeviceList=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(meterDeviceList)
        updateMeter()
        card("Connection help","Bio Track retries interrupted connections up to three times and keeps readings already saved. The ongoing meter notification keeps an active session visible. On HyperOS, check app battery restrictions if background transfers stop. A sleeping meter may need waking before it can reconnect.")
        card("Device support","This preview implements the Bluetooth SIG glucose profile. Accu-Chek Instant hardware validation is pending. Other models require protocol and hardware verification; continuous glucose sensors are not supported in this preview.")
    }
    private fun updateMeter() {
        val list=meterDeviceList ?: return
        meterStatus?.let { if(it.text.toString()!=bio.meter.status) it.text=bio.meter.status }
        fun enable(button: Button,enabled: Boolean) {
            if(button.isEnabled!=enabled) {
                button.isEnabled=enabled
                button.backgroundTintList=android.content.res.ColorStateList.valueOf(if(enabled) teal else Color.GRAY)
            }
        }
        meterActionButtons.forEach { (button,enabled) -> enable(button,enabled()) }
        val devices=bio.meter.devices.toMap()
        meterDeviceButtons.keys.filter { it !in devices }.forEach { list.removeView(meterDeviceButtons.remove(it)) }
        devices.forEach { (address,name) ->
            val label="$name · ${address.takeLast(5)}"
            val row=meterDeviceButtons.getOrPut(address) { button(list,label,!bio.meter.busy) { startSession(address) } }
            if(row.text.toString()!=label) row.text=label
            enable(row,!bio.meter.busy)
        }
    }
    private fun share() {
        content.addView(text("Share when you choose",28f,ink,true))
        val rows=bio.store.readings().filter { it.eligible }
        val pending=rows.count { !it.exported && it.timeConfirmed }
        val review=rows.count { !it.timeConfirmed }
        card("Health Connect","$pending ready to share · ${rows.count { it.exported }} shared · $review need time review").apply {
            addView(text("Meter import never automatically shares your readings. Review meter times in History first.",14f,muted))
            if(review>0) button(this,"Review $review meter reading times") {
                val unconfirmed=rows.filter { !it.timeConfirmed }
                AlertDialog.Builder(this@MainActivity).setTitle("Review meter dates and times")
                    .setMessage("${unconfirmed.size} readings, from ${date(unconfirmed.last().time)} to ${date(unconfirmed.first().time)}.\n\nThese use the meter’s clock interpreted in ${ZoneId.systemDefault()}. Confirm only if this date range and the times shown in History are correct.\n\nLatest readings:\n"+unconfirmed.take(5).joinToString("\n") { "${date(it.time)} · ${value(it.mmol)} $unit" })
                    .setNegativeButton("Review in History") { _,_ -> go("History") }.setPositiveButton("Times are correct") { _,_ -> unconfirmed.forEach { bio.store.confirm(it.id) }; render() }.show()
            }
            button(this,"Allow Health Connect sharing",!shareBusy) {
                if(exporter.available()) healthPermission.launch(HealthExporter.permissions)
                else message("Health Connect is unavailable. Enable or update Health Connect in Android settings or Google Play.")
            }
            button(this,if(shareBusy) "Sharing…" else "Share $pending pending readings",!shareBusy && pending>0) {
                shareBusy=true; render()
                lifecycleScope.launch {
                    try { val count=withContext(Dispatchers.IO) { exporter.export() }; message("$count readings shared to Health Connect") }
                    catch(e: Exception) { message("Sharing stopped: ${e.localizedMessage}. Saved readings remain available; retry to continue.") }
                    finally { shareBusy=false; render() }
                }
            }
        }
        card("Google Fit and other health apps","After sharing to Health Connect, allow your destination app to read blood glucose if it supports that data type. Availability and display depend on that app. Bio Track does not directly write to Google Fit, Google Health or Zepp in this preview.")
        button(content,"Export all readings as CSV") { csv.launch("BioTrack-${LocalDate.now()}.csv") }
        button(content,"Privacy and permissions") { startActivity(Intent(this,PrivacyActivity::class.java)) }
    }
    private fun settings() {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        val units=Switch(this).apply { text="Display in mmol/L"; isChecked=mmol; minHeight=dp(52) }; form.addView(units)
        form.addView(text("Target range in mmol/L (display changes do not alter stored readings)",14f,muted))
        val lower=EditText(this).apply { hint="Lower target"; inputType=8194; setText(String.format(Locale.getDefault(),"%.1f",low)) }; form.addView(lower)
        val upper=EditText(this).apply { hint="Upper target"; inputType=8194; setText(String.format(Locale.getDefault(),"%.1f",high)) }; form.addView(upper)
        val dialog=AlertDialog.Builder(this).setTitle("Display & targets").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val l=lower.text.toString().replace(',','.').toDoubleOrNull(); val h=upper.text.toString().replace(',','.').toDoubleOrNull()
            if(l==null || h==null || !l.isFinite() || !h.isFinite() || l<=0 || h<=l || h>50) { upper.error="Enter a valid range up to 50 mmol/L"; return@setOnClickListener }
            prefs.edit().putBoolean("mmol",units.isChecked).putFloat("low",l.toFloat()).putFloat("high",h.toFloat()).apply(); dialog.dismiss(); render()
        } }; dialog.show()
    }
    private fun manual() {
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        form.addView(text("Recorded at the current phone time. Unit: $unit",14f,muted))
        val glucose=EditText(this).apply { hint="Glucose ($unit)"; inputType=8194 }; form.addView(glucose)
        val tags=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("No meal tag","Fasting","Before meal","After meal","Bedtime")) }; form.addView(tags)
        val note=EditText(this).apply { hint="Optional note"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }; form.addView(note)
        val dialog=AlertDialog.Builder(this).setTitle("Add a reading").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val v=glucose.text.toString().replace(',','.').toDoubleOrNull(); val canonical=v?.let { if(mmol) it else it/GlucoseProtocol.MG_PER_MMOL }
            if(canonical==null || !canonical.isFinite() || canonical<=0 || canonical>50) { glucose.error="Enter a valid glucose value"; return@setOnClickListener }
            bio.store.manual(canonical,note.text.toString().trim(),if(tags.selectedItemPosition>0) tags.selectedItem.toString() else "")
            dialog.dismiss(); render()
        } }; dialog.show()
    }
    private fun message(s: String) { AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show() }
    private inner class TrendView(private val rows: List<Reading>,private val low: Double,private val high: Double,private val mmol: Boolean): View(this@MainActivity) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription="Glucose chart, ${rows.size} readings. Values and times are available in History." }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left=dp(44).toFloat(); val right=width-dp(10).toFloat(); val top=dp(18).toFloat(); val bottom=height-dp(34).toFloat()
            val minTime=rows.minOf { it.time }; val maxTime=rows.maxOf { it.time }; val ceiling=maxOf(high+2,rows.maxOf { it.mmol }+1)
            fun y(v: Double)=bottom-((v/ceiling)*(bottom-top)).toFloat()
            paint.color=Color.rgb(223,242,233); canvas.drawRect(left,y(high),right,y(low),paint)
            paint.textSize=dp(11).toFloat(); paint.color=muted
            for(v in listOf(0.0,low,high,ceiling)) canvas.drawText(value(v),0f,y(v),paint)
            rows.forEach { r ->
                val x=if(maxTime==minTime) (left+right)/2 else left+((r.time-minTime).toDouble()/(maxTime-minTime)*(right-left)).toFloat()
                paint.color=if(r.mmol in low..high) teal else Color.rgb(181,91,44); canvas.drawCircle(x,y(r.mmol),dp(4).toFloat(),paint)
            }
            paint.color=muted; canvas.drawText(date(minTime),left,height-dp(8).toFloat(),paint)
        }
    }
}

class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ScrollView(this).apply { addView(TextView(this@PrivacyActivity).apply {
            textSize=18f; setPadding(32,80,32,40)
            text="Bio Track privacy\n\nReadings are stored in this app’s private local database. Android backup is disabled. No advertising, analytics, account or network permission is included.\n\nBluetooth is used only to find and connect to the meter you select. An active meter session has a foreground notification and can be disconnected.\n\nHealth Connect blood glucose write permission is optional. Readings are shared only when you tap Share. Bio Track does not read your other health records. Revoke access in Health Connect settings at any time.\n\nCSV export contains personal health data; choose its destination carefully. Uninstalling Bio Track removes its local database. Records already shared to Health Connect remain there; manage them in Health Connect.\n\nThis is a preview. Hardware compatibility and background behavior require testing. Use your meter’s own display for treatment decisions."
        }) })
    }
}
