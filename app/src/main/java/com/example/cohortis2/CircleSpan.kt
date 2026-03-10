package com.example.cohortis2

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

class CircleSpan(
    private val backgroundColor: Int,
    private val textColor: Int
) : ReplacementSpan() {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val textWidth = paint.measureText(text, start, end)
        val size = (paint.descent() - paint.ascent()).roundToInt()
        return (textWidth.coerceAtLeast(size.toFloat()) + 16).toInt() // Ensure it's wide enough for the circle
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val textWidth = paint.measureText(text, start, end)
        val fm = paint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        
        // Calculate circle diameter based on text size
        val diameter = textHeight + 4 
        val radius = diameter / 2f
        
        val width = getSize(paint, text, start, end, null)
        val centerX = x + width / 2f
        val centerY = (top + bottom) / 2f

        val oldColor = paint.color
        val oldStyle = paint.style

        // Draw background circle
        paint.color = backgroundColor
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)

        // Draw text centered in the circle
        paint.color = textColor
        // Draw text with vertical centering
        val textBaselineY = centerY - (fm.descent + fm.ascent) / 2f
        canvas.drawText(text!!, start, end, centerX - textWidth / 2f, textBaselineY, paint)

        // Restore paint
        paint.color = oldColor
        paint.style = oldStyle
    }
}
