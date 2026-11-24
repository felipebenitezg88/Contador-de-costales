package com.example.contadordecostales

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Dibuja encima de la cámara:
 *  - la zona de la banda (rectángulo azul translúcido)
 *  - la línea de conteo (roja) con una flecha de dirección
 *  - los costales que la app está considerando (cajas verdes)
 *
 * Las coordenadas del análisis son de la imagen completa; aquí se
 * convierten al área visible de la vista (la cámara usa FIT_CENTER).
 */
class CountOverlay(context: Context) : View(context) {

    private var imageWidth = 0
    private var imageHeight = 0

    private var lineX = 0.5f
    private var zoneTop = 0.2f
    private var zoneBottom = 0.8f
    private var leftToRight = true

    private var boxes: List<RectF> = emptyList()

    private val zonePaint = Paint().apply {
        color = Color.argb(40, 56, 189, 248)
        style = Paint.Style.FILL
    }
    private val zoneBorderPaint = Paint().apply {
        color = Color.argb(160, 56, 189, 248)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val linePaint = Paint().apply {
        color = Color.rgb(239, 68, 68)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val boxPaint = Paint().apply {
        color = Color.rgb(75, 230, 150)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val arrowPaint = Paint().apply {
        color = Color.rgb(239, 68, 68)
        textSize = 56f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun setConfig(
        lineX: Float,
        zoneTop: Float,
        zoneBottom: Float,
        leftToRight: Boolean
    ) {
        this.lineX = lineX
        this.zoneTop = zoneTop
        this.zoneBottom = zoneBottom
        this.leftToRight = leftToRight
        invalidate()
    }

    /** Llamar desde el hilo principal. */
    fun showDetections(width: Int, height: Int, boxes: List<RectF>) {
        imageWidth = width
        imageHeight = height
        this.boxes = boxes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth == 0 || imageHeight == 0) return

        val scale = min(
            width.toFloat() / imageWidth,
            height.toFloat() / imageHeight
        )
        val drawW = imageWidth * scale
        val drawH = imageHeight * scale
        val offsetX = (width - drawW) / 2f
        val offsetY = (height - drawH) / 2f

        val top = offsetY + drawH * zoneTop
        val bottom = offsetY + drawH * zoneBottom
        val left = offsetX
        val right = offsetX + drawW

        canvas.drawRect(left, top, right, bottom, zonePaint)
        canvas.drawRect(left, top, right, bottom, zoneBorderPaint)

        val x = offsetX + drawW * lineX
        canvas.drawLine(x, top, x, bottom, linePaint)
        canvas.drawText(
            if (leftToRight) "▶" else "◀",
            x,
            top + arrowPaint.textSize,
            arrowPaint
        )

        for (box in boxes) {
            canvas.drawRect(
                offsetX + box.left * scale,
                offsetY + box.top * scale,
                offsetX + box.right * scale,
                offsetY + box.bottom * scale,
                boxPaint
            )
        }
    }
}
