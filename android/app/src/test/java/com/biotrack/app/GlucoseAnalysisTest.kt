package com.biotrack.app

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class GlucoseAnalysisTest {
    private val zone=ZoneId.of("Asia/Kuala_Lumpur")
    private fun config(start: String="2026-10-01",end: String="2026-10-07",source: String="All sources",tag: String="All tags") = ReportConfig(LocalDate.parse(start),LocalDate.parse(end),zone,3.9,10.0,true,source,tag)
    private fun reading(day: String="2026-10-02",hour: Int=8,value: Double=6.0,source: String="meter",tag: String="",eligible: Boolean=true,id: String="$day-$hour-$value") =
        Reading(id,value,LocalDate.parse(day).atTime(hour,0).atZone(zone).toInstant().toEpochMilli(),source,"",false,eligible,true,tag)
    private val now=LocalDate.parse("2026-10-20").atStartOfDay(zone).toInstant().toEpochMilli()
    @Test fun exactQuartilesAndPopulationSd() {
        val stats=GlucoseAnalysis.stats(listOf(2.0,4.0,6.0,8.0).map { reading(value=it) },3.9,10.0)
        assertEquals(5.0,stats.mean!!,0.0); assertEquals(5.0,stats.median!!,0.0)
        assertEquals(3.5,stats.q1!!,0.0); assertEquals(6.5,stats.q3!!,0.0)
        assertEquals(kotlin.math.sqrt(5.0),stats.sd!!,0.000001)
    }
    @Test fun emptyAndSingleSampleDoNotInventVariability() {
        val empty=GlucoseAnalysis.stats(emptyList(),3.9,10.0); assertNull(empty.mean); assertNull(empty.percent(0))
        val single=GlucoseAnalysis.stats(listOf(reading()),3.9,10.0); assertNull(single.cv); assertEquals(0.0,single.sd!!,0.0)
    }
    @Test fun configuredTargetIncludesBothEndpoints() {
        val stats=GlucoseAnalysis.stats(listOf(3.8,3.9,10.0,10.1).map { reading(value=it) },3.9,10.0)
        assertEquals(1,stats.below); assertEquals(2,stats.within); assertEquals(1,stats.above)
    }
    @Test fun filtersUseLocalCalendarDatesAndKeepMissingDays() {
        val report=GlucoseAnalysis.build(listOf(reading("2026-10-01",0),reading("2026-10-07",23),reading("2026-10-08",0)),config(),now)
        assertEquals(2,report.rows.size); assertEquals(7,report.days.size); assertEquals(2,report.measuredDays)
        assertEquals(5,report.days.count { it.rows.isEmpty() })
    }
    @Test fun comparisonIsPrecedingEqualCalendarPeriodWithoutOverlap() {
        val c=config(); assertEquals(LocalDate.parse("2026-09-24"),c.previousStart); assertEquals(LocalDate.parse("2026-09-30"),c.previousEnd)
        val report=GlucoseAnalysis.build(listOf(reading("2026-09-30"),reading("2026-10-01")),c,now)
        assertEquals(1,report.rows.size); assertEquals(1,report.previous.size)
    }
    @Test fun sourceAndExplicitTagFiltersApplyToBothPeriods() {
        val all=listOf(reading(source="Manual",tag="Fasting"),reading(source="meter",tag="Fasting"),reading(source="meter"),reading("2026-09-30",source="meter",tag="Fasting"))
        val report=GlucoseAnalysis.build(all,config(source="Meter only",tag="Fasting"),now)
        assertEquals(1,report.rows.size); assertEquals(1,report.previous.size)
    }
    @Test fun clockDoesNotInferFasting() {
        val report=GlucoseAnalysis.build(listOf(reading(hour=6)),config(),now)
        assertEquals(0,report.meals.first { it.label=="Fasting" }.stats.count)
        assertEquals(1,report.meals.first { it.label=="Untagged" }.stats.count)
    }
    @Test fun repeatRuleNeedsDistinctDaysNotRepeatedTests() {
        val sameDay=(6..8).map { reading(hour=it,value=12.0) }
        assertTrue(GlucoseAnalysis.build(sameDay,config(),now).patterns.isEmpty())
        val across=listOf("2026-10-01","2026-10-02","2026-10-03").map { reading(it,7,12.0) }
        assertEquals(1,GlucoseAnalysis.build(across,config(),now).patterns.size)
    }
    @Test fun flaggedInvalidAndFutureValuesAreExcludedWithCounts() {
        val future=reading("2026-10-07",23)
        val report=GlucoseAnalysis.build(listOf(reading(),reading(value=8.0,eligible=false),reading(value=Double.NaN),future),config(),reading("2026-10-07",12).time)
        assertEquals(1,report.rows.size); assertEquals(2,report.excluded); assertEquals(1,report.future)
    }
    @Test fun hourlyAndWeekdayPartitionsDoNotLoseRecords() {
        val rows=(0..23).map { reading(hour=it) }; val report=GlucoseAnalysis.build(rows,config(),now)
        assertEquals(24,report.hourly.sumOf { it.rows.size }); assertEquals(24,report.weekdays.sumOf { it.rows.size })
        assertTrue(report.hourly.all { it.stats.count==1 })
    }
    @Test fun dstDayUsesCalendarBoundaries() {
        val ny=ZoneId.of("America/New_York"); val c=ReportConfig(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-01"),ny,3.9,10.0,true)
        val start=c.start.atStartOfDay(ny).toInstant(); val end=c.end.plusDays(1).atStartOfDay(ny).toInstant()
        assertEquals(25,Duration.between(start,end).toHours())
        val a=reading().copy(time=start.plusSeconds(3600).toEpochMilli()); val b=a.copy(id="second",time=start.plusSeconds(7200).toEpochMilli())
        val r=GlucoseAnalysis.build(listOf(a,b),c,end.toEpochMilli())
        assertEquals(2,r.rows.size); assertEquals(1,r.measuredDays)
    }
    @Test fun csvIncludesBothUnitsAndEscapesFormulaNotes() {
        val r=reading(value=5.6).copy(userNote="=SUM(1,2)\n\"meal\"")
        val csv=GlucoseAnalysis.csv(GlucoseAnalysis.build(listOf(r),config(),now))
        assertTrue(csv.contains("glucose_mmol_l,glucose_mg_dl")); assertTrue(csv.contains("'=SUM")); assertTrue(csv.contains("\"\"meal\"\""))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsReversedRange() { config("2026-10-07","2026-10-01") }
    @Test(expected=IllegalArgumentException::class) fun rejectsOverlongRange() { config("2025-01-01","2026-10-01") }
}
