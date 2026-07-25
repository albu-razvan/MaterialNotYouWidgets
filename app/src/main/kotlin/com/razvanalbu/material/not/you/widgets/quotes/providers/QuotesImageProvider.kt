package com.razvanalbu.material.not.you.widgets.quotes.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.ContextThemeWrapper
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.razvanalbu.material.not.you.widgets.core.BaseWidgetImageProvider
import com.razvanalbu.material.not.you.widgets.core.WidgetUtils
import com.razvanalbu.material.not.you.widgets.quotes.QuotesStore
import com.razvanalbu.material.not.you.widgets.R as AppR
import androidx.core.graphics.withTranslation

class QuotesImageProvider : BaseWidgetImageProvider() {

    override val authoritySuffix = ".quoteswidgetimages"
    override val cacheKeyPrefix = "quote"
    override val logTag = "QuotesImageProvider"

    override fun getWidgetDimensions(context: Context, widgetId: Int): Pair<Int, Int> {
        return WidgetUtils.getSizePx(context, widgetId)
    }

    override fun renderContent(context: Context, widgetId: Int, width: Int, height: Int): Bitmap {
        return renderQuoteBitmap(context, widgetId, width, height)
    }

    companion object {
        fun nextGeneration(widgetId: Int) = BaseWidgetImageProvider.nextGeneration(widgetId)

        fun invalidateCache(widgetId: Int) = BaseWidgetImageProvider.invalidateCache(widgetId)

        fun uri(packageName: String, widgetId: Int): Uri =
            uri(packageName, widgetId, ".quoteswidgetimages")
    }
}

internal fun renderQuoteBitmap(
    context: Context,
    widgetId: Int,
    width: Int,
    height: Int
): Bitmap {
    val quote = QuotesStore.loadCurrentQuote(context, widgetId)

    val themedContext = ContextThemeWrapper(
        context,
        com.google.android.material.R.style.Theme_Material3Expressive_DynamicColors_DayNight
    )

    val attrs = themedContext.obtainStyledAttributes(
        intArrayOf(
            com.google.android.material.R.attr.colorOnSurface,
            android.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary,
        )
    )
    val textColor = attrs.getColor(0, 0xFF1C1B1F.toInt())
    val primaryColor = attrs.getColor(1, 0xFF6750A4.toInt())
    val onPrimaryColor = attrs.getColor(2, 0xFFFFFFFF.toInt())
    attrs.recycle()

    val regularTypeface = ResourcesCompat.getFont(
        context, AppR.font.google_sans_flex_regular
    ) ?: Typeface.DEFAULT
    val italicTypeface = ResourcesCompat.getFont(
        context, AppR.font.google_sans_flex_quote
    ) ?: regularTypeface
    val boldRoundedTypeface = ResourcesCompat.getFont(
        context, AppR.font.google_sans_flex_bold_rounded
    ) ?: regularTypeface

    val padding = (minOf(width, height) * 0.07f).toInt().coerceAtLeast(8)
    val availableWidth = width - padding * 2
    val availableHeight = height - padding * 2

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    if (quote != null) {
        val quoteTextSize = fitTextSize(
            quote.text, italicTypeface, availableWidth,
            (availableHeight * 0.75f).toInt(), minOf(width, height) * 0.10f
        )
        val authorTextSize = (quoteTextSize * 0.85f).coerceAtLeast(10f)

        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            this.typeface = italicTypeface
            textSize = quoteTextSize
        }

        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onPrimaryColor
            this.typeface = boldRoundedTypeface
            textAlign = Paint.Align.RIGHT
            textSize = authorTextSize
        }

        val quoteLayout = StaticLayout.Builder.obtain(
            quote.text, 0, quote.text.length, quotePaint, availableWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .build()

        val authorTextWidth = authorPaint.measureText(quote.author)
        val pillHPadding = authorTextSize * 0.5f
        val pillVPadding = authorTextSize * 0.15f
        val pillWidth = authorTextWidth + pillHPadding * 2
        val textHeight = authorPaint.descent() - authorPaint.ascent()
        val pillHeight = textHeight + pillVPadding * 2
        val pillRadius = pillHeight / 2f

        val gap = authorTextSize * 0.35f
        val totalContentHeight = quoteLayout.height + gap + pillHeight
        val startY = (height - totalContentHeight) / 2f + pillHeight * 0.25f

        canvas.withTranslation(padding.toFloat(), startY) {
            quoteLayout.draw(this)
        }

        val pillRight = width - padding.toFloat()
        val pillLeft = pillRight - pillWidth
        val textCenterY =
            startY + quoteLayout.height + gap + pillHeight / 2f
        val authorBaselineY =
            textCenterY - (authorPaint.ascent() + authorPaint.descent()) / 2f

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            pillLeft,
            textCenterY - pillHeight / 2f,
            pillRight,
            textCenterY + pillHeight / 2f,
            pillRadius, pillRadius,
            pillPaint
        )

        canvas.drawText(
            quote.author,
            pillRight - pillHPadding,
            authorBaselineY,
            authorPaint
        )
    }

    return bitmap
}

internal fun fitTextSize(
    text: String,
    typeface: Typeface,
    maxWidth: Int,
    maxHeight: Int,
    startSize: Float,
): Float {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
    }

    paint.textSize = startSize

    val staticLayout = StaticLayout.Builder.obtain(
        text, 0, text.length, paint, maxWidth
    )
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setLineSpacing(0f, 1.0f)
        .setIncludePad(true)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
        .build()

        if (staticLayout.height <= maxHeight && staticLayout.lineCount <= 6) {
        return startSize
    }

    var low = 4f
    var high = startSize
    var best = low
    var iterations = 0

    while (low <= high && iterations < 20) {
        val mid = (low + high) / 2f
        paint.textSize = mid

        val layout = StaticLayout.Builder.obtain(
            text, 0, text.length, paint, maxWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED)
            .build()

        if (layout.height <= maxHeight && layout.lineCount <= 6) {
            best = mid
            low = mid + 1f
        } else {
            high = mid - 1f
        }
        iterations++
    }

    return best
}
