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
    private const val KEY_CURRENT_TEXT = "current_text_"
    private const val KEY_CURRENT_AUTHOR = "current_author_"
    private const val KEY_SHUFFLE_LIST = "shuffle_list_"
    private const val KEY_SHUFFLE_CURSOR = "shuffle_cursor_"
    private const val KEY_SHUFFLE_GEN = "shuffle_gen_"
    private const val KEY_QUOTES_GEN = "quotes_gen_"

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
        val gen = prefs(context).getInt(KEY_QUOTES_GEN + widgetId, 0) + 1
        prefs(context).edit {
            putString(KEY_QUOTES + widgetId, json.toString())
            putInt(KEY_QUOTES_GEN + widgetId, gen)
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

    fun saveCurrentQuote(context: Context, widgetId: Int, quote: Quote) {
        prefs(context).edit {
            putString(KEY_CURRENT_TEXT + widgetId, quote.text)
            putString(KEY_CURRENT_AUTHOR + widgetId, quote.author)
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

        if (quotes.size == 1) {
            saveCurrentQuote(context, widgetId, quotes[0])
            return quotes[0]
        }

        val preferences = prefs(context)
        val quotesGen = preferences.getInt(KEY_QUOTES_GEN + widgetId, 0)
        val storedShuffleGen = preferences.getInt(KEY_SHUFFLE_GEN + widgetId, -1)
        val shuffleJson = preferences.getString(KEY_SHUFFLE_LIST + widgetId, null)
        var cursor = preferences.getInt(KEY_SHUFFLE_CURSOR + widgetId, 0)

        var shuffleList: MutableList<Int>? = null

        if (shuffleJson != null && storedShuffleGen == quotesGen) {
            val parsed = JSONArray(shuffleJson).let { arr ->
                (0 until arr.length()).map { arr.getInt(it) }.toMutableList()
            }

            if (parsed.size == quotes.size) {
                shuffleList = parsed
            }
        }

        if (shuffleList == null) {
            val lastFromPrevGen = if (shuffleJson != null) {
                JSONArray(shuffleJson).let { arr ->
                    if (arr.length() > 0) arr.getInt(arr.length() - 1) else -1
                }
            } else -1

            shuffleList = generateShuffle(quotes.size, lastFromPrevGen)
            cursor = 0
        }

        if (cursor >= shuffleList.size) {
            shuffleList = generateShuffle(quotes.size, shuffleList.last())
            cursor = 0
        }

        val index = shuffleList[cursor]
        cursor++

        preferences.edit {
            putString(KEY_SHUFFLE_LIST + widgetId, JSONArray(shuffleList).toString())
            putInt(KEY_SHUFFLE_CURSOR + widgetId, cursor)
            putInt(KEY_SHUFFLE_GEN + widgetId, quotesGen)
        }

        val quote = quotes[index]
        saveCurrentQuote(context, widgetId, quote)

        return quote
    }

    private fun generateShuffle(size: Int, avoidFirst: Int): MutableList<Int> {
        val list = (0 until size).toMutableList()
        for (i in size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }

        if (avoidFirst in 0..<size && list[0] == avoidFirst && size > 1) {
            val temp = list[0]
            list[0] = list[1]
            list[1] = temp
        }

        return list
    }

    fun removeQuotesData(context: Context, widgetId: Int) {
        prefs(context).edit {
            remove(KEY_QUOTES + widgetId)
            remove(KEY_CURRENT_TEXT + widgetId)
            remove(KEY_CURRENT_AUTHOR + widgetId)
            remove(KEY_SHUFFLE_LIST + widgetId)
            remove(KEY_SHUFFLE_CURSOR + widgetId)
            remove(KEY_SHUFFLE_GEN + widgetId)
            remove(KEY_QUOTES_GEN + widgetId)
        }
    }
}
