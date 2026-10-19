package com.biotrack.app

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.sqrt

data class ReportConfig(val start: LocalDate,val end: LocalDate,val zone: ZoneId,
    val low: Double,val high: Double,val mmol: Boolean,val source: String="All sources",val tag: String="All tags") {
    init { require(!end.isBefore(start) && ChronoUnit.DAYS.between(start,end)<366); require(low.isFinite() && high.isFinite() && low>0 && high>low) }
    val days get()=ChronoUnit.DAYS.between(start,end).toInt()+1
    val previousEnd get()=start.minusDays(1)
    val previousStart get()=start.minusDays(days.toLong())
    val unit get()=if(mmol) "mmol/L" else "mg/dL"
    fun value(v: Double?)=v?.let { String.format(Locale.getDefault(),if(mmol) "%.1f" else "%.0f",if(mmol) it else it*GlucoseProtocol.MG_PER_MMOL) } ?: "—"
    fun local(r: Reading)=Instant.ofEpochMilli(r.time).atZone(zone)
}

data class GlucoseStats(val count: Int,val mean: Double?,val median: Double?,val q1: Double?,val q3: Double?,
    val sd: Double?,val cv: Double?,val min: Double?,val max: Double?,val below: Int,val within: Int,val above: Int) {
    fun percent(count: Int): Double?=if(this.count==0) null else count*100.0/this.count
}
data class AnalysisGroup(val label: String,val rows: List<Reading>,val stats: GlucoseStats,val measuredDays: Int)
data class DailyGlucose(val date: LocalDate,val rows: List<Reading>,val stats: GlucoseStats)
data class GlucoseReport(val config: ReportConfig,val rows: List<Reading>,val previous: List<Reading>,val stats: GlucoseStats,
    val previousStats: GlucoseStats,val days: List<DailyGlucose>,val timeGroups: List<AnalysisGroup>,
    val weekdays: List<AnalysisGroup>,val meals: List<AnalysisGroup>,val hourly: List<AnalysisGroup>,val patterns: List<String>,
    val excluded: Int,val future: Int,val generatedAt: Long) {
    val measuredDays get()=days.count { it.rows.isNotEmpty() }
    val unconfirmed get()=rows.count { !it.timeConfirmed }
    val sparse get()=rows.size<10 || measuredDays<3
}

object GlucoseAnalysis {
    val MEAL_TAGS=listOf("Fasting","Before meal","After meal","Bedtime")
    const val METHODS="Fingerstick/spot readings only. Percentages count readings, not time in range. No CGM AGP, GMI or estimated HbA1c is calculated. Mean/SD weight each reading equally; SD is population SD; quartiles use linear interpolation. Unmeasured periods are unknown. Time-of-day grouping does not establish fasting or meal timing. Targets are user configured. Review with your care team before changing treatment."
    const val PATTERN_METHOD="Descriptive repeat rule: below or above the configured target in the same 3-hour block on at least 3 distinct measured days in this report. Repeated tests on one day do not establish a recurring pattern. This is not a validated clinical alert or prediction."
    fun quantile(values: List<Double>,p: Double): Double? {
        require(p in 0.0..1.0)
        if(values.isEmpty()) return null
        val sorted=values.sorted(); val at=(sorted.size-1)*p; val index=floor(at).toInt()
        return sorted[index]+(sorted[minOf(index+1,sorted.lastIndex)]-sorted[index])*(at-index)
    }
    fun stats(rows: List<Reading>,low: Double,high: Double): GlucoseStats {
        val v=rows.map { it.mmol }; val mean=v.takeIf { it.isNotEmpty() }?.average()
        val sd=mean?.let { m -> sqrt(v.map { (it-m)*(it-m) }.average()) }
        return GlucoseStats(v.size,mean,quantile(v,0.5),quantile(v,0.25),quantile(v,0.75),sd,
            if(mean!=null && mean>0 && v.size>1) sd!!/mean*100 else null,v.minOrNull(),v.maxOrNull(),
            v.count { it<low },v.count { it>=low && it<=high },v.count { it>high })
    }
    fun build(all: List<Reading>,config: ReportConfig,now: Long=System.currentTimeMillis()): GlucoseReport {
        fun matches(r: Reading)=when(config.source) { "Meter only" -> r.source!="Manual"; "Manual only" -> r.source=="Manual"; else -> true } &&
            (config.tag=="All tags" || if(config.tag=="Untagged") r.mealTag.isEmpty() else r.mealTag==config.tag)
        fun inDates(r: Reading,start: LocalDate,end: LocalDate): Boolean { val d=config.local(r).toLocalDate(); return !d.isBefore(start) && !d.isAfter(end) }
        fun valid(r: Reading)=r.eligible && r.mmol.isFinite() && r.mmol>0
        val selected=all.filter { matches(it) && inDates(it,config.start,config.end) }
        val rows=selected.filter { valid(it) && it.time<=now }.sortedBy { it.time }
        val previous=all.filter { matches(it) && valid(it) && it.time<=now && inDates(it,config.previousStart,config.previousEnd) }.sortedBy { it.time }
        fun group(label: String,items: List<Reading>)=AnalysisGroup(label,items,stats(items,config.low,config.high),items.map { config.local(it).toLocalDate() }.distinct().size)
        val days=(0 until config.days).map { offset -> val date=config.start.plusDays(offset.toLong()); val items=rows.filter { config.local(it).toLocalDate()==date }; DailyGlucose(date,items,stats(items,config.low,config.high)) }
        val timeGroups=(0..7).map { bin -> group("%02d:00–%02d:00".format(bin*3,(bin+1)*3),rows.filter { config.local(it).hour/3==bin }) }
        val weekdays=DayOfWeek.entries.map { day -> group(day.name.lowercase().replaceFirstChar { it.titlecase() },rows.filter { config.local(it).dayOfWeek==day }) }
        val meals=(MEAL_TAGS+"Untagged").map { tag -> group(tag,rows.filter { if(tag=="Untagged") it.mealTag.isEmpty() else it.mealTag==tag }) }
        val hourly=(0..23).map { hour -> group("%02d:00–%02d:00".format(hour,hour+1),rows.filter { config.local(it).hour==hour }) }
        val patterns=buildList {
            timeGroups.forEach { g ->
                for((label,predicate) in listOf<Pair<String,(Reading)->Boolean>>("below" to { it.mmol<config.low },"above" to { it.mmol>config.high })) {
                    val flagged=g.rows.filter(predicate); val distinct=flagged.map { config.local(it).toLocalDate() }.distinct().size
                    if(distinct>=3) add("${g.label}: $label target on $distinct of ${g.measuredDays} measured days (${flagged.size}/${g.rows.size} readings).")
                }
            }
        }
        return GlucoseReport(config,rows,previous,stats(rows,config.low,config.high),stats(previous,config.low,config.high),days,timeGroups,weekdays,meals,hourly,patterns,
            selected.count { !valid(it) },selected.count { valid(it) && it.time>now },now)
    }
    fun csv(report: GlucoseReport): String {
        fun quote(s: String): String {
            val safe=if(s.firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$s" else s
            return "\""+safe.replace("\"","\"\"")+"\""
        }
        val c=report.config
        return "id,time_utc,time_local,timezone,glucose_mmol_l,glucose_mg_dl,source,meal_tag,user_note,time_confirmed,shared\r\n"+
            report.rows.joinToString("") { r -> listOf(r.id,Instant.ofEpochMilli(r.time).toString(),c.local(r).toLocalDateTime().toString(),c.zone.id,
                r.mmol.toString(),(r.mmol*GlucoseProtocol.MG_PER_MMOL).toString(),r.source,r.mealTag,r.userNote,r.timeConfirmed.toString(),r.exported.toString()).joinToString(",",transform=::quote)+"\r\n" }
    }
}
