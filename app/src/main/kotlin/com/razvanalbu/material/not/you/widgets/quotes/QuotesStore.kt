package com.razvanalbu.material.not.you.widgets.quotes

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Quote(
    val text: String,
    val author: String,
)

internal object QuotesStore {
    private const val PREFS_NAME = "quotes_widget_data"
    private const val KEY_QUOTES = "quotes_"
    private const val KEY_CURRENT_INDEX = "current_index_"
    private const val KEY_CURRENT_TEXT = "current_text_"
    private const val KEY_CURRENT_AUTHOR = "current_author_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveQuotes(context: Context, widgetId: Int, quotes: List<Quote>) {
        val json = JSONArray().apply {
            quotes.forEach { q ->
                put(JSONObject().apply {
                    put("text", q.text)
                    put("author", q.author)
                })
            }
        }
        prefs(context).edit {
            putString(KEY_QUOTES + widgetId, json.toString())
        }
    }

    fun loadQuotes(context: Context, widgetId: Int): List<Quote> {
        val json = prefs(context).getString(KEY_QUOTES + widgetId, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Quote(obj.getString("text"), obj.getString("author"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCurrentQuote(context: Context, widgetId: Int, quote: Quote, index: Int) {
        prefs(context).edit {
            putString(KEY_CURRENT_TEXT + widgetId, quote.text)
            putString(KEY_CURRENT_AUTHOR + widgetId, quote.author)
            putInt(KEY_CURRENT_INDEX + widgetId, index)
        }
    }

    fun loadCurrentQuote(context: Context, widgetId: Int): Quote? {
        val prefs = prefs(context)
        val text = prefs.getString(KEY_CURRENT_TEXT + widgetId, null) ?: return null
        val author = prefs.getString(KEY_CURRENT_AUTHOR + widgetId, null) ?: ""

        return Quote(text, author)
    }

    fun pickRandomQuote(context: Context, widgetId: Int): Quote? {
        val quotes = loadQuotes(context, widgetId)
        if (quotes.isEmpty()) {
            return null
        }

        val index = Random.nextInt(quotes.size)
        val quote = quotes[index]

        saveCurrentQuote(context, widgetId, quote, index)

        return quote
    }

    fun removeQuotesData(context: Context, widgetId: Int) {
        prefs(context).edit {
            remove(KEY_QUOTES + widgetId)
            remove(KEY_CURRENT_TEXT + widgetId)
            remove(KEY_CURRENT_AUTHOR + widgetId)
            remove(KEY_CURRENT_INDEX + widgetId)
        }
    }
}
