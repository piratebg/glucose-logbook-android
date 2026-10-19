package com.biotrack.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

class InsightsPanel(private val activity: ComponentActivity,private val store: ReadingStore,private val prefs: SharedPreferences,
    private val annotate: (Reading)->Unit,private val pdf: (GlucoseReport)->Unit,private val csv: (GlucoseReport)->Unit): LinearLayout(activity) {
    private val teal=Color.rgb(8,127,114); private val ink=Color.rgb(24,48,45); private val muted=Color.rgb(87,108,104)
    private val sections=listOf("Summary","Patterns","Time of day","Logbook","Reports")
    private var section=prefs.getString("report_section","Summary") ?: "Summary"
    private lateinit var report: GlucoseReport
    init { orientation=VERTICAL; reload() }
    private fun dp(v: Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(s: String,size: Float=15f,bold: Boolean=false,color: Int=ink)=TextView(context).apply {
        text=s; textSize=size; setTextColor(color); if(bold) setTypeface(typeface,Typeface.BOLD); setPadding(0,dp(5),0,dp(5))
    }
    private fun card(title: String,detail: String?=null): LinearLayout {
        val box=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(14)); background=GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(20).toFloat() } }
        addView(box,LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
        box.addView(text(title,19f,true)); detail?.let { box.addView(text(it,14f,false,muted)) }; return box
    }
    private fun action(parent: LinearLayout,label: String,run: ()->Unit)=Button(context).apply {
        text=label; isAllCaps=false; textSize=14f; minHeight=dp(48); setTextColor(Color.WHITE)
        backgroundTintList=android.content.res.ColorStateList.valueOf(teal); setOnClickListener { run() }
        parent.addView(this,LayoutParams(-1,-2).apply { topMargin=dp(6) })
    }
    private fun percent(v: Double?)=v?.let { String.format(Locale.getDefault(),"%.1f%%",it) } ?: "—"
    private fun scalar(v: Double?)=v?.let { String.format(Locale.getDefault(),"%.1f",it) } ?: "—"
    private fun config(): ReportConfig {
        val today=LocalDate.now(); val days=prefs.getInt("days",14)
        val start=runCatching { LocalDate.parse(prefs.getString("report_start",null)) }.getOrNull() ?: today.minusDays(days-1L)
        val end=runCatching { LocalDate.parse(prefs.getString("report_end",null)) }.getOrNull() ?: today
        return ReportConfig(start,end,ZoneId.systemDefault(),prefs.getFloat("low",3.9f).toString().toDouble(),prefs.getFloat("high",10f).toString().toDouble(),prefs.getBoolean("mmol",true),
            prefs.getString("report_source","All sources") ?: "All sources",prefs.getString("report_tag","All tags") ?: "All tags")
    }
    private fun reload() {
        removeAllViews(); report=GlucoseAnalysis.build(store.readings(),config())
        val c=report.config
        addView(text("Your glucose, in detail",27f,true))
        addView(text("${c.start} to ${c.end} · ${c.unit}",14f,false,muted))
        val quick=LinearLayout(context).apply { orientation=HORIZONTAL }
        for(days in listOf(7,14,30,90)) quick.addView(Button(context).apply {
            text="${days}d"; isAllCaps=false; minHeight=dp(48)
            setOnClickListener { prefs.edit().putInt("days",days).remove("report_start").remove("report_end").apply(); reload() }
        },LayoutParams(0,-2,1f))
        addView(quick)
        action(this,"Dates & filters · ${c.source} · ${c.tag}") { filters() }
        val selector=Spinner(context).apply {
            contentDescription="Analysis section"; minimumHeight=dp(56)
            adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,sections)
            setSelection(sections.indexOf(section).coerceAtLeast(0))
            onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?)=Unit
                override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                    if(section!=sections[position]) { section=sections[position]; prefs.edit().putString("report_section",section).apply(); reload() }
                }
            }
        }; addView(selector)
        when(section) { "Summary" -> summary(); "Patterns" -> patterns(); "Time of day" -> timeOfDay(); "Logbook" -> logbook(); else -> reports() }
    }
    private fun summary() {
        val c=report.config; val s=report.stats
        card("Data coverage", "${s.count} readings across ${report.measuredDays}/${c.days} calendar days").apply {
            addView(text("${scalar(s.count.toDouble()/c.days)} readings per calendar day · ${scalar(if(report.measuredDays==0) null else s.count.toDouble()/report.measuredDays)} per measured day",14f))
            addView(text("${c.days-report.measuredDays} days without readings · ${report.unconfirmed} times unconfirmed · ${report.excluded} flagged numeric records excluded · ${report.future} future-dated excluded",14f,false,muted))
            addView(text("${report.rows.count { it.source!="Manual" }} meter · ${report.rows.count { it.source=="Manual" }} manual · ${report.rows.count { it.mealTag.isNotEmpty() }} explicitly tagged",14f))
            if(report.sparse) addView(text("Limited sampling: fewer than 10 readings or fewer than 3 measured days. Patterns may not represent your usual glucose.",14f,true))
        }
        if(s.count==0) { card("No matching readings","Change the dates or filters, or import readings from your meter."); return }
        card("Glucose summary", "Mean ${c.value(s.mean)} · Median ${c.value(s.median)} ${c.unit}").apply {
            addView(text("Lowest ${c.value(s.min)} · Highest ${c.value(s.max)} ${c.unit}"))
            addView(text("Middle 50%: ${c.value(s.q1)}–${c.value(s.q3)} ${c.unit}"))
            addView(text("Population SD ${c.value(s.sd)} ${c.unit} · Sampled-reading CV ${percent(s.cv)}",14f))
            addView(text("CV describes these spot readings; CGM variability targets are not applied.",13f,false,muted))
        }
        card("Your configured target","${c.value(c.low)}–${c.value(c.high)} ${c.unit}").apply {
            addView(text("Below: ${s.below}/${s.count} (${percent(s.percent(s.below))})"))
            addView(text("Within: ${s.within}/${s.count} (${percent(s.percent(s.within))})"))
            addView(text("Above: ${s.above}/${s.count} (${percent(s.percent(s.above))})"))
            addView(text("Percentages of readings, not time in range. Repeated testing can change these proportions.",13f,false,muted))
        }
        card("Recorded glucose over time","Tap a dot for its time, value and context. Unmeasured gaps are not interpolated.").apply { addView(ReportChartView(context,report,false,annotate),LayoutParams(-1,dp(270))) }
        comparison()
        referenceBands()
    }
    private fun comparison() {
        val c=report.config; val a=report.stats; val b=report.previousStats
        card("Previous period comparison","${c.previousStart} to ${c.previousEnd} · same filters and targets").apply {
            addView(text("Readings: ${b.count} → ${a.count}"))
            if(a.count==0 || b.count==0) { addView(text("Both periods need readings for a comparison.",14f,false,muted)); return@apply }
            fun delta(v: Double)=if(v>=0) "+${c.value(v)}" else c.value(v)
            addView(text("Mean: ${c.value(b.mean)} → ${c.value(a.mean)} ${c.unit} (${delta(a.mean!!-b.mean!!)})"))
            addView(text("Median: ${c.value(b.median)} → ${c.value(a.median)} ${c.unit}"))
            val difference=a.percent(a.within)!!-b.percent(b.within)!!
            addView(text("Within target: ${percent(b.percent(b.within))} → ${percent(a.percent(a.within))} (${String.format(Locale.getDefault(),"%+.1f",difference)} percentage points)"))
            addView(text("Descriptive change only. Testing frequency, meal mix and coverage may differ. Today's period may still be incomplete.",13f,false,muted))
        }
    }
    private fun referenceBands() {
        val c=report.config; val rows=report.rows
        card("Reference glucose bands","Fixed reporting cutoffs in mmol/L; these do not replace your personal target.").apply {
            val counts=listOf(rows.count { it.mmol<3.0 },rows.count { it.mmol>=3.0 && it.mmol<3.9 },rows.count { it.mmol>=3.9 && it.mmol<=10.0 },rows.count { it.mmol>10.0 && it.mmol<=13.9 },rows.count { it.mmol>13.9 })
            val labels=listOf("< ${c.value(3.0)}","${c.value(3.0)} to < ${c.value(3.9)}","${c.value(3.9)}–${c.value(10.0)}","> ${c.value(10.0)} to ${c.value(13.9)}","> ${c.value(13.9)}")
            counts.forEachIndexed { i,n -> addView(text("${labels[i]} ${c.unit}: $n (${percent(report.stats.percent(n))})",14f)) }
            addView(text("Counts of recorded values, not episode counts, duration or clinical severity. Unit labels are rounded; classification uses the mmol/L cutoffs.",13f,false,muted))
        }
    }
    private fun patterns() {
        card("Repeated time-of-day observations",GlucoseAnalysis.PATTERN_METHOD).apply {
            if(report.patterns.isEmpty()) addView(text("No block met this rule. This does not rule out highs or lows between tests.",14f))
            report.patterns.forEach { addView(text(it,15f,true)) }
        }
        card("Meal-tag breakdown","Tags must be entered explicitly. Morning measurements are not assumed to be fasting.")
        report.meals.forEach { groupCard(it) }
        card("Weekday patterns","Compare sample counts before interpreting averages.")
        report.weekdays.forEach { groupCard(it) }
    }
    private fun groupCard(g: AnalysisGroup) {
        val c=report.config; val s=g.stats
        card(g.label,"${s.count} readings · ${g.measuredDays} measured days").apply {
            if(s.count==0) addView(text("No data",14f,false,muted)) else {
                addView(text("Mean ${c.value(s.mean)} · Median ${c.value(s.median)} ${c.unit}"))
                addView(text("Low–high ${c.value(s.min)}–${c.value(s.max)} · Within target ${percent(s.percent(s.within))}",14f))
                if(s.count>=3) addView(text("Middle 50% ${c.value(s.q1)}–${c.value(s.q3)} ${c.unit}",14f))
            }
        }
    }
    private fun timeOfDay() {
        card("24-hour overlay","Each dot is one actual reading on the phone's current timezone. It is a spot-reading overlay, not a CGM AGP.").apply {
            addView(ReportChartView(context,report,true,annotate),LayoutParams(-1,dp(290)))
        }
        card("Three-hour blocks","Counts, medians and quartiles use recorded samples only. A clock block does not establish meal timing.")
        report.timeGroups.forEach { groupCard(it) }
        val hourly=prefs.getBoolean("report_hourly",false)
        action(this,if(hourly) "Hide hourly detail" else "Show all 24 hourly summaries") { prefs.edit().putBoolean("report_hourly",!hourly).apply(); reload() }
        if(hourly) report.hourly.forEach { groupCard(it) }
    }
    private fun logbook() {
        val c=report.config
        card("Daily logbook","All ${c.days} selected calendar days are listed. Missing days stay visible. Tap a day to inspect every value and add meal context.")
        report.days.asReversed().forEach { day ->
            val s=day.stats
            card(day.date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),if(s.count==0) "No readings recorded" else "${s.count} readings · Mean ${c.value(s.mean)} · Median ${c.value(s.median)} ${c.unit}").apply {
                if(s.count>0) {
                    addView(text("Low–high ${c.value(s.min)}–${c.value(s.max)} · Below ${s.below} / within ${s.within} / above ${s.above}",14f))
                    action(this,"View ${s.count} readings") { showDay(day) }
                }
            }
        }
    }
    private fun showDay(day: DailyGlucose) {
        val c=report.config
        val labels=day.rows.map { "${c.local(it).format(DateTimeFormatter.ofPattern("HH:mm"))} · ${c.value(it.mmol)} ${c.unit} · ${it.mealTag.ifEmpty { "Untagged" }}${if(!it.timeConfirmed) " · Time unconfirmed" else ""}" }
        AlertDialog.Builder(context).setTitle(day.date.toString()).setItems(labels.toTypedArray()) { _,i -> annotate(day.rows[i]) }.setNegativeButton("Close",null).show()
    }
    private fun reports() {
        card("Clinician review report","Includes selected dates, filters, targets, coverage, previous-period comparison, glucose distributions, daily/clock charts, weekday and meal summaries, daily logbook and every included reading.").apply {
            action(this,"Save detailed PDF report") { pdf(report) }
            action(this,"Export filtered readings as CSV") { csv(report) }
        }
        card("How to interpret this report",GlucoseAnalysis.METHODS)
        card("Pattern method",GlucoseAnalysis.PATTERN_METHOD)
        card("Sources & limitations","Informed by mySugr logbook reports, Glooko BG report types, OneTouch Reveal BGM patient summaries and ADA/NIDDK glucose-monitoring guidance. This app's report and repeat rules are not medically validated, and do not recommend medication changes.").apply {
            addView(text("Sources: mysugr.com · support.glooko.com · onetouch.com · doi.org/10.2337/dc26-s006 · niddk.nih.gov",13f,false,muted))
        }
    }
    private fun filters() {
        val c=report.config; var start=c.start; var end=c.end
        val form=LinearLayout(context).apply { orientation=VERTICAL; setPadding(dp(18),dp(8),dp(18),dp(8)) }
        val startButton=action(form,"From: $start") {}
        val endButton=action(form,"To: $end") {}
        fun pick(initial: LocalDate,done: (LocalDate)->Unit) {
            DatePickerDialog(context,{ _,y,m,d -> done(LocalDate.of(y,m+1,d)) },initial.year,initial.monthValue-1,initial.dayOfMonth).apply {
                datePicker.maxDate=System.currentTimeMillis(); show()
            }
        }
        startButton.setOnClickListener { pick(start) { start=it; startButton.text="From: $it" } }
        endButton.setOnClickListener { pick(end) { end=it; endButton.text="To: $it" } }
        fun select(label: String,items: List<String>,current: String): Spinner {
            form.addView(text(label,14f,true))
            return Spinner(context).apply { adapter=ArrayAdapter(context,android.R.layout.simple_spinner_dropdown_item,items); setSelection(items.indexOf(current).coerceAtLeast(0)); minimumHeight=dp(48); form.addView(this) }
        }
        val sources=listOf("All sources","Meter only","Manual only"); val tags=listOf("All tags")+GlucoseAnalysis.MEAL_TAGS+"Untagged"
        val source=select("Reading source",sources,c.source); val tag=select("Meal context",tags,c.tag)
        val error=text("",13f,false,Color.RED); form.addView(error)
        val dialog=AlertDialog.Builder(context).setTitle("Report dates & filters").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>=366) { error.text="Choose 1–366 days, with the end after the start."; return@setOnClickListener }
            prefs.edit().putString("report_start",start.toString()).putString("report_end",end.toString()).putString("report_source",source.selectedItem.toString()).putString("report_tag",tag.selectedItem.toString()).apply()
            dialog.dismiss(); reload()
        } }; dialog.show()
    }
}
