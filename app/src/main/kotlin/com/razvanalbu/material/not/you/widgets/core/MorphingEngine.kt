package com.razvanalbu.material.not.you.widgets.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapeType {
    PILL,
    COOKIE
}

class MorphingEngine(private val numPoints: Int = 48) {

    private val radiiBuffer = FloatArray(numPoints)
    private val px = FloatArray(numPoints)
    private val py = FloatArray(numPoints)
    private val path = Path()

    fun computeRadii(type: ShapeType): FloatArray {
        return FloatArray(numPoints) { i ->
            val theta = 2.0 * PI * i / numPoints

            when (type) {
                ShapeType.PILL -> pillRadius(theta)
                ShapeType.COOKIE -> cloverRadius(theta)
            }
        }
    }

    private fun pillRadius(theta: Double): Float {
        val maxR = PILL_HALF_WIDTH + PILL_CORNER_RADIUS
        val total = sqrt(PILL_HALF_WIDTH * PILL_HALF_WIDTH + PILL_CORNER_RADIUS * PILL_CORNER_RADIUS)
        val transitionSin = PILL_CORNER_RADIUS / total

        val cosT = cos(theta).toFloat()
        val sinT = sin(theta).toFloat()
        val absSinT = abs(sinT)

        val raw = if (absSinT <= transitionSin) {
            val centerX = if (cosT >= 0f) PILL_HALF_WIDTH else -PILL_HALF_WIDTH
            centerX * cosT + sqrt(PILL_CORNER_RADIUS * PILL_CORNER_RADIUS
                    - PILL_HALF_WIDTH * PILL_HALF_WIDTH * sinT * sinT)
        } else {
            PILL_CORNER_RADIUS / absSinT
        }

        return raw / maxR
    }

    private fun cloverRadius(theta: Double): Float {
        val depth = 0.06f
        val raw = 1f - depth * cos(6 * theta).toFloat()
        val maxR = 1f + depth

        return raw / maxR
    }

    fun morphRadii(rA: FloatArray, rB: FloatArray, t: Float): FloatArray {
        for (i in 0 until numPoints) {
            radiiBuffer[i] = rA[i] * (1f - t) + rB[i] * t
        }

        return radiiBuffer
    }

    private fun toPoints(radii: FloatArray, cx: Float, cy: Float, scale: Float, rotRad: Double) {
        for (i in 0 until numPoints) {
            val theta = 2.0 * PI * i / numPoints + rotRad

            px[i] = cx + cos(theta).toFloat() * radii[i] * scale
            py[i] = cy + sin(theta).toFloat() * radii[i] * scale
        }
    }

    private fun buildSmoothPath() {
        path.rewind()
        path.moveTo(px[0], py[0])

        val n = numPoints
        for (i in 0 until n) {
            val prev = px[(i - 1 + n) % n] to py[(i - 1 + n) % n]
            val cur = px[i] to py[i]
            val next = px[(i + 1) % n] to py[(i + 1) % n]
            val next2 = px[(i + 2) % n] to py[(i + 2) % n]

            path.cubicTo(cur.first + (next.first - prev.first) / 6f,
                cur.second + (next.second - prev.second) / 6f,
                next.first - (next2.first - cur.first) / 6f,
                next.second - (next2.second - cur.second) / 6f,
                next.first,
                next.second)
        }
        path.close()
    }

    fun renderRadiiToBitmap(
        width: Int,
        height: Int,
        rA: FloatArray,
        rB: FloatArray,
        t: Float,
        fillColor: Int,
        rotationDeg: Float = 0f
    ): Bitmap {
        val radii = morphRadii(rA, rB, t)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) * SHAPE_SCALE
        val rotRad = rotationDeg * PI / 180.0

        toPoints(radii, cx, cy, scale, rotRad)
        buildSmoothPath()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }

        canvas.drawPath(path, paint)

        return bitmap
    }

    companion object {
        const val PILL_HALF_WIDTH = 0.3f
        const val PILL_CORNER_RADIUS = 1f
        const val SHAPE_SCALE = 0.5f
    }
}
