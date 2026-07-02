package com.razvanalbu.material.not.you.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ShapeType {
    PILL,
    HEXAGON
}

class MorphingEngine(private val numPoints: Int = 48) {

    private val path = Path()
    private val radiiBuffer = FloatArray(numPoints)

    fun computeRadii(type: ShapeType): FloatArray {
        return FloatArray(numPoints) { i ->
            val theta = 2.0 * PI * i / numPoints
            when (type) {
                ShapeType.PILL -> pillRadius(theta)
                ShapeType.HEXAGON -> hexagonRadius(theta)
            }
        }
    }

    private fun pillRadius(theta: Double): Float {
        val hw = 2f
        val cr = 1f
        val total = sqrt(hw * hw + cr * cr)
        val transitionSin = cr / total

        val cosT = cos(theta).toFloat()
        val sinT = sin(theta).toFloat()
        val absSinT = abs(sinT)

        return if (absSinT <= transitionSin) {
            val centerX = if (cosT >= 0f) hw else -hw
            centerX * cosT + sqrt(cr * cr - hw * hw * sinT * sinT)
        } else {
            cr / absSinT
        }
    }

    private fun hexagonRadius(theta: Double): Float {
        val n = 6
        val halfEdge = (PI / n).toFloat()
        val vertexAngle = (2.0 * PI / n).toFloat()
        val normalized = ((theta % vertexAngle) + vertexAngle) % vertexAngle
        val phi = normalized - halfEdge
        return (cos(halfEdge) / cos(phi)).toFloat()
    }

    fun morphRadii(rA: FloatArray, rB: FloatArray, t: Float): FloatArray {
        for (i in 0 until numPoints) {
            radiiBuffer[i] = rA[i] * (1f - t) + rB[i] * t
        }
        return radiiBuffer
    }

    fun renderToBitmap(
        width: Int,
        height: Int,
        typeA: ShapeType,
        typeB: ShapeType,
        t: Float,
        fillColor: Int,
        rotationDeg: Float = 0f
    ): Bitmap {
        val rA = computeRadii(typeA)
        val rB = computeRadii(typeB)
        return renderRadiiToBitmap(width, height, rA, rB, t, fillColor, rotationDeg)
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val cy = height / 2f
        val scale = minOf(width, height) * 0.38f
        val rotRad = rotationDeg * PI / 180.0

        path.rewind()
        for (i in 0 until numPoints) {
            val theta = 2.0 * PI * i / numPoints + rotRad
            val x = cx + cos(theta).toFloat() * radii[i] * scale
            val y = cy + sin(theta).toFloat() * radii[i] * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        canvas.drawPath(path, paint)

        return bitmap
    }
}
