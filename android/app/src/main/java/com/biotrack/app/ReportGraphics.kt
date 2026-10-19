package com.biotrack.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import java.time.format.DateTimeFormatter
import kotlin.math.hypot

object ReportGraphics {
    private val teal=Color.rgb(8,127,114)
    fun chart(canvas: Canvas,bounds: RectF,report: GlucoseReport,overlay: Boolean): List<Pair<Reading,PointF>> {
        val c=report.config; val scale=bounds.width()/330f
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=10f*scale }
        val left=bounds.left+37*scale; val right=bounds.right-12*scale; val top=bounds.top+20*scale; val bottom=bounds.bottom-32*scale
        val ceiling=maxOf(c.high+2,report.rows.maxOfOrNull { it.mmol }?.plus(1) ?: 12.0)
        fun y(v: Double)=bottom-((v/ceiling)*(bottom-top)).toFloat()
        paint.color=Color.rgb(225,242,234); canvas.drawRect(left,y(c.high),right,y(c.low),paint)
        paint.strokeWidth=scale
        for(v in listOf(0.0,c.low,c.high,ceiling)) {
            paint.color=Color.rgb(207,219,215); canvas.drawLine(left,y(v),right,y(v),paint)
            paint.color=Color.DKGRAY; canvas.drawText(c.value(v),bounds.left,y(v)+3*scale,paint)
        }
        paint.color=Color.DKGRAY; canvas.drawText(c.unit,left,bounds.top+10*scale,paint)
        val start=c.start.atStartOfDay(c.zone).toInstant().toEpochMilli()
        val end=c.end.plusDays(1).atStartOfDay(c.zone).toInstant().toEpochMilli()
        val dots=report.rows.map { r ->
            val fraction=if(overlay) (c.local(r).toLocalTime().toSecondOfDay()/86400.0) else (r.time-start).toDouble()/(end-start)
            val point=PointF(left+(fraction*(right-left)).toFloat(),y(r.mmol))
            paint.color=if(r.mmol<c.low) Color.rgb(183,58,66) else if(r.mmol>c.high) Color.rgb(168,92,26) else teal
            paint.alpha=170; canvas.drawCircle(point.x,point.y,2.8f*scale,paint); r to point
        }
        paint.alpha=255; paint.color=Color.DKGRAY
        if(overlay) {
            for(hour in listOf(0,6,12,18,24)) {
                val x=left+(right-left)*hour/24f; val label="%02d".format(hour)
                canvas.drawText(label,x-paint.measureText(label)/2,bottom+18*scale,paint)
            }
        } else {
            val first=c.start.format(DateTimeFormatter.ofPattern("d MMM")); val last=c.end.format(DateTimeFormatter.ofPattern("d MMM"))
            canvas.drawText(first,left,bottom+18*scale,paint); canvas.drawText(last,right-paint.measureText(last),bottom+18*scale,paint)
        }
        return dots
    }
}

class ReportChartView(context: Context,private val report: GlucoseReport,private val overlay: Boolean,private val select: (Reading)->Unit): View(context) {
    private var dots=emptyList<Pair<Reading,PointF>>()
    init { contentDescription="${if(overlay) "24-hour overlay" else "Glucose timeline"}, ${report.rows.size} recorded readings. Tap a dot for details, or use the Logbook for accessible reading lists."; isClickable=true }
    override fun onDraw(canvas: Canvas) { super.onDraw(canvas); dots=ReportGraphics.chart(canvas,RectF(0f,0f,width.toFloat(),height.toFloat()),report,overlay) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(event.action==MotionEvent.ACTION_UP) {
            val closest=dots.minByOrNull { hypot((event.x-it.second.x).toDouble(),(event.y-it.second.y).toDouble()) }
            if(closest!=null && hypot((event.x-closest.second.x).toDouble(),(event.y-closest.second.y).toDouble())<30*resources.displayMetrics.density) {
                performClick(); select(closest.first); return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
