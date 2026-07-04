package com.razvanalbu.material.not.you.widgets.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.FontVariationAxis

object VariableFontProvider {

    private data class Variation(
        val wght: Float = 400f,
        val wdth: Float = 100f,
        val grad: Float = 0f,
        val rond: Float = 0f,
        val slnt: Float = 0f,
    )

    private val cache = mutableMapOf<Variation, Typeface>()

    @SuppressLint("ResourceType")
    fun get(
        context: Context,
        wght: Float = 100f,
        wdth: Float = 100f,
        grad: Float = 0f,
        rond: Float = 0f,
        slnt: Float = 0f,
    ): Typeface {
        val variation = Variation(wght, wdth, grad, rond, slnt)

        return cache.getOrPut(variation) {
            Typeface.Builder(context.assets, "fonts/google_sans_flex.ttf")
                .setFontVariationSettings(
                    arrayOf(
                        FontVariationAxis("wght", wght),
                        FontVariationAxis("wdth", wdth),
                        FontVariationAxis("GRAD", grad),
                        FontVariationAxis("ROND", rond),
                        FontVariationAxis("slnt", slnt),
                    )
                )
                .build()
        }
    }
}