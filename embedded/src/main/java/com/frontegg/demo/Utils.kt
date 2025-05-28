package com.frontegg.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

fun createTextDrawable(text: String, context: Context): Drawable {
    val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gray700)
        textSize = 12f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    val width = (24 * context.resources.displayMetrics.density).toInt()
    val height = (24 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw circle background
    val circlePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gray200)
        isAntiAlias = true
    }
    canvas.drawCircle(width / 2f, height / 2f, width / 2f, circlePaint)

    // Draw text
    val textBounds = Rect()
    paint.getTextBounds(text, 0, text.length, textBounds)
    val x = width / 2f
    val y = height / 2f + (textBounds.height() / 2f) - textBounds.bottom
    canvas.drawText(text, x, y, paint)

    return BitmapDrawable(context.resources, bitmap)
}