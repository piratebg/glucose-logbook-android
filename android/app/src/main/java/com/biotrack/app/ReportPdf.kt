package com.biotrack.app

import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Offline, vector PDF generated from the same immutable analysis snapshot used in the UI. */
object ReportPdf {
    fun write(report: GlucoseReport,out: OutputStream,demo: Boolean=false) {
        val c=report.config; val s=report.stats
        val pdf=PdfDocument(); val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        var page: PdfDocument.Page?=null; var number=0; var y=0f
        val teal=Color.rgb(8,127,114); val ink=Color.rgb(24,48,45)
        fun finish() { page?.let { pdf.finishPage(it) }; page=null }
        fun newPage() {
            finish(); number++
            page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
            val canvas=page!!.canvas
            paint.color=teal; canvas.drawRect(0f,0f,595f,12f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.BOLD); paint.textSize=17f; paint.color=ink
            canvas.drawText(if(demo) "Bio Track | SAMPLE - synthetic data" else "Bio Track | Glucose review",40f,42f,paint)
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL); paint.textSize=9f
            canvas.drawText("${c.start} to ${c.end} | ${c.unit} | ${c.zone.id}",40f,59f,paint)
            paint.color=Color.GRAY; canvas.drawLine(40f,795f,555f,795f,paint)
            paint.textSize=8f; canvas.drawText("Spot readings, not continuous monitoring. Review with your care team.",40f,810f,paint)
            canvas.drawText("Page $number",510f,810f,paint); y=83f
        }
        fun ensure(height: Float) { if(page==null || y+height>777f) newPage() }
        fun wrap(text: String,width: Float,size: Float): List<String> {
            paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL); paint.textSize=size
            return text.replace('\r',' ').replace('–','-').replace('—','-').split('\n').flatMap { paragraph ->
                var remaining=paragraph; val lines=mutableListOf<String>()
                if(remaining.isEmpty()) lines.add("")
                while(remaining.isNotEmpty()) {
                    var count=paint.breakText(remaining,true,width,null).coerceAtLeast(1)
                    if(count<remaining.length) { val space=remaining.lastIndexOf(' ',count-1); if(space>0) count=space }
                    lines.add(remaining.take(count).trimEnd()); remaining=remaining.drop(count).trimStart()
                }
                lines
            }
        }
        fun paragraph(text: String,size: Float=10f) {
            wrap(text,515f,size).forEach { line ->
                ensure(size+5); paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL); paint.textSize=size; paint.color=ink
                page!!.canvas.drawText(line,40f,y+size,paint); y+=size+4
            }; y+=4
        }
        fun heading(title: String) {
            ensure(48f); y+=8; paint.typeface=Typeface.create("sans-serif",Typeface.BOLD); paint.textSize=14f; paint.color=teal
            page!!.canvas.drawText(title,40f,y+14,paint); y+=27
        }
        fun percent(v: Double?)=v?.let { String.format(Locale.getDefault(),"%.1f%%",it) } ?: "No data"
        fun table(headers: List<String>,widths: List<Float>,rows: List<List<String>>) {
            fun draw(cells: List<String>,header: Boolean) {
                val lines=cells.mapIndexed { i,v -> wrap(v,widths[i]-10,9f) }
                val height=lines.maxOfOrNull { it.size }?.times(12f)?.plus(10f) ?: 22f
                if(header) { paint.color=Color.rgb(225,242,234); page!!.canvas.drawRect(40f,y,555f,y+height,paint) }
                var x=40f
                cells.indices.forEach { i ->
                    paint.typeface=Typeface.create("sans-serif",if(header) Typeface.BOLD else Typeface.NORMAL); paint.textSize=9f; paint.color=ink
                    lines[i].forEachIndexed { j,line -> page!!.canvas.drawText(line,x+5,y+13+j*12,paint) }; x+=widths[i]
                }
                y+=height; paint.color=Color.LTGRAY; paint.strokeWidth=0.4f; page!!.canvas.drawLine(40f,y,555f,y,paint)
            }
            ensure(60f); draw(headers,true)
            rows.forEach { cells ->
                val height=cells.mapIndexed { i,v -> wrap(v,widths[i]-10,9f).size }.max()*12f+10f
                if(y+height>777f) { newPage(); draw(headers,true) }
                draw(cells,false)
            }; y+=10
        }
        fun groups(title: String,groups: List<AnalysisGroup>) {
            heading(title)
            table(listOf("Group","N / days","Mean","Median","Q1-Q3","Within"),listOf(110f,65f,65f,65f,120f,90f),groups.map { g ->
                listOf(g.label,"${g.stats.count} / ${g.measuredDays}",c.value(g.stats.mean),c.value(g.stats.median),if(g.stats.count>=3) "${c.value(g.stats.q1)}-${c.value(g.stats.q3)}" else "Too few",percent(g.stats.percent(g.stats.within)))
            })
        }
        try {
            newPage()
            paragraph("Generated ${Instant.ofEpochMilli(report.generatedAt).atZone(c.zone).format(DateTimeFormatter.ofPattern("d MMM uuuu HH:mm z"))}. Filters: ${c.source}; ${c.tag}. Configured target ${c.value(c.low)}-${c.value(c.high)} ${c.unit}.")
            heading("Coverage and data quality")
            paragraph("${s.count} included readings across ${report.measuredDays}/${c.days} calendar days. ${c.days-report.measuredDays} days without readings. ${report.rows.count { it.source!="Manual" }} meter / ${report.rows.count { it.source=="Manual" }} manual. ${report.rows.count { it.mealTag.isNotEmpty() }} explicitly meal-tagged. ${report.unconfirmed} unconfirmed timestamps. ${report.excluded} flagged numeric records and ${report.future} future-dated records excluded.")
            if(report.sparse) paragraph("LIMITED SAMPLING: fewer than 10 readings or 3 measured days. No conclusion about usual glucose or unmeasured periods can be drawn from this coverage.")
            heading("Recorded glucose summary")
            table(listOf("Statistic","Selected period","Previous period"),listOf(185f,165f,165f),listOf(
                listOf("Dates","${c.start} to ${c.end}","${c.previousStart} to ${c.previousEnd}"),
                listOf("Reading count",s.count.toString(),report.previousStats.count.toString()),
                listOf("Mean (${c.unit})",c.value(s.mean),c.value(report.previousStats.mean)),
                listOf("Median (${c.unit})",c.value(s.median),c.value(report.previousStats.median)),
                listOf("Q1-Q3 (${c.unit})","${c.value(s.q1)}-${c.value(s.q3)}","${c.value(report.previousStats.q1)}-${c.value(report.previousStats.q3)}"),
                listOf("Min-max (${c.unit})","${c.value(s.min)}-${c.value(s.max)}","${c.value(report.previousStats.min)}-${c.value(report.previousStats.max)}"),
                listOf("Population SD (${c.unit})",c.value(s.sd),c.value(report.previousStats.sd)),
                listOf("Sampled-reading CV",percent(s.cv),percent(report.previousStats.cv)),
                listOf("Below configured target","${s.below} (${percent(s.percent(s.below))})","${report.previousStats.below} (${percent(report.previousStats.percent(report.previousStats.below))})"),
                listOf("Within configured target","${s.within} (${percent(s.percent(s.within))})","${report.previousStats.within} (${percent(report.previousStats.percent(report.previousStats.within))})"),
                listOf("Above configured target","${s.above} (${percent(s.percent(s.above))})","${report.previousStats.above} (${percent(report.previousStats.percent(report.previousStats.above))})")
            ))
            paragraph("Comparison uses the preceding ${c.days} calendar days and the same filters. Differences may reflect testing frequency, meal mix and missing data. Today may be incomplete. Percentages refer to readings, never time.",9f)
            heading("Reference glucose bands")
            val bandCounts=listOf(report.rows.count { it.mmol<3.0 },report.rows.count { it.mmol>=3.0 && it.mmol<3.9 },report.rows.count { it.mmol>=3.9 && it.mmol<=10.0 },report.rows.count { it.mmol>10.0 && it.mmol<=13.9 },report.rows.count { it.mmol>13.9 })
            val bandLabels=listOf("< ${c.value(3.0)}","${c.value(3.0)} to < ${c.value(3.9)}","${c.value(3.9)}-${c.value(10.0)}","> ${c.value(10.0)} to ${c.value(13.9)}","> ${c.value(13.9)}")
            table(listOf("Reference band (${c.unit})","Readings","Percentage"),listOf(235f,140f,140f),bandCounts.mapIndexed { i,n -> listOf(bandLabels[i],n.toString(),percent(s.percent(n))) })
            paragraph("Classification uses fixed mmol/L cutoffs; converted labels are rounded. These bands do not replace the configured personal target. Counts do not establish episode duration or clinical severity.",9f)
            ensure(250f); heading("Recorded glucose timeline")
            ensure(205f); ReportGraphics.chart(page!!.canvas,RectF(40f,y,555f,y+190f),report,false); y+=205
            paragraph("Each dot is an observed value. No line fills unmeasured intervals.",9f)
            ensure(250f); heading("24-hour spot-reading overlay")
            ensure(205f); ReportGraphics.chart(page!!.canvas,RectF(40f,y,555f,y+190f),report,true); y+=205
            paragraph("Clock times use ${c.zone.id}. This is not a CGM ambulatory glucose profile.",9f)
            groups("Time of day (three-hour blocks)",report.timeGroups)
            groups("Hourly detail",report.hourly)
            heading("Repeated-day observations")
            paragraph(GlucoseAnalysis.PATTERN_METHOD,9f)
            if(report.patterns.isEmpty()) paragraph("No block met the repeat rule. This does not rule out unobserved highs/lows.") else report.patterns.forEach { paragraph(it) }
            groups("Explicit meal context",report.meals)
            paragraph("Fasting and meal context are user-entered, not inferred from clock time. Before/after meal differences are not paired because meal events are not linked.",9f)
            groups("Day of week",report.weekdays)
            heading("Daily logbook")
            table(listOf("Date","N","Mean","Min-max","Below / within / above"),listOf(105f,40f,85f,110f,175f),report.days.map { d ->
                listOf(d.date.toString(),d.stats.count.toString(),c.value(d.stats.mean),if(d.rows.isEmpty()) "No readings" else "${c.value(d.stats.min)}-${c.value(d.stats.max)}","${d.stats.below} / ${d.stats.within} / ${d.stats.above}")
            })
            heading("Included reading details")
            if(report.rows.isEmpty()) paragraph("No included readings for the selected filters.")
            report.rows.forEach { r ->
                ensure(55f)
                paragraph("${c.local(r).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"))} | ${c.value(r.mmol)} ${c.unit} | ${if(r.source=="Manual") "Manual" else "Meter"} | ${r.mealTag.ifEmpty { "Untagged" }} | ${if(r.timeConfirmed) "Time reviewed" else "Time unconfirmed"} | ${if(r.exported) "Shared" else "Local"}",9f)
                if(r.userNote.isNotBlank()) paragraph("Note: ${r.userNote}",9f)
            }
            ensure(320f); heading("Methods, limitations and references")
            paragraph(GlucoseAnalysis.METHODS,9f)
            paragraph("No treatment recommendation, risk score, estimated HbA1c or GMI is generated. Sparse fingerstick testing can miss highs and lows. This report is a discussion aid, not a validated clinical decision tool.",9f)
            paragraph("Reporting concepts: mySugr logbook reports; Glooko BG overview/logbook/overlay; OneTouch Reveal BGM patient summary. Clinical context: ADA Standards of Care 2026, section 6; NIDDK Managing Diabetes. Accessed 19 Oct 2026.",9f)
            listOf("mysugr.com/blog/how-to-use-mysugr-logbook-reports","support.glooko.com/hc/en-us/articles/115003869129","onetouch.com/s/article/OneTouch-Reveal-Web-Application-Patient-Summary-Report","doi.org/10.2337/dc26-s006","niddk.nih.gov/health-information/diabetes/overview/managing-diabetes").forEach { paragraph(it,8f) }
            finish(); pdf.writeTo(out)
        } finally { if(page!=null) finish(); pdf.close() }
    }
}
