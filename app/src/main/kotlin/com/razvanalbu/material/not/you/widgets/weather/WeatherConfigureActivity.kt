package com.razvanalbu.material.not.you.widgets.weather

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.razvanalbu.material.not.you.widgets.R
import kotlin.math.max
import kotlin.random.Random

class WeatherConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var isImeAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContentView(R.layout.activity_weather_configure)

        setResult(RESULT_CANCELED, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupWindowInsets()
        setupSearch()

        findViewById<EditText>(R.id.search_input).clearFocus()
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun setupWindowInsets() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val layoutRoot = findViewById<ViewGroup>(R.id.root_layout)

        root.clipToPadding = false
        root.clipChildren = false
        layoutRoot.clipToPadding = false
        layoutRoot.clipChildren = false

        ViewCompat.setOnApplyWindowInsetsListener(layoutRoot) { view, insets ->
            if (!isImeAnimating) {
                updatePaddingForInsets(view, insets)
            }

            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            layoutRoot,
            object : WindowInsetsAnimationCompat.Callback(
                DISPATCH_MODE_CONTINUE_ON_SUBTREE
            ) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        isImeAnimating = true
                    }

                    super.onPrepare(animation)
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    updatePaddingForInsets(layoutRoot, insets)

                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if ((animation.typeMask and WindowInsetsCompat.Type.ime()) != 0) {
                        isImeAnimating = false
                        ViewCompat.requestApplyInsets(layoutRoot)
                    }

                    super.onEnd(animation)
                }
            }
        )

        ViewCompat.requestApplyInsets(layoutRoot)
    }

    private fun updatePaddingForInsets(view: View, insets: WindowInsetsCompat) {
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

        view.updatePadding(
            left = systemBars.left,
            top = systemBars.top,
            right = systemBars.right,
            bottom = max(systemBars.bottom, ime.bottom)
        )
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.search_input)
        val clearButton = findViewById<ImageView>(R.id.clear_button)
        val currentConfig = WidgetConfig.load(this, appWidgetId)

        if (currentConfig != null) {
            searchInput.setText(currentConfig.displayName)
            searchInput.setSelection(searchInput.text.length)
        }

        clearButton.setOnClickListener {
            searchInput.text.clear()
            searchInput.requestFocus()
            WindowInsetsControllerCompat(window, searchInput).show(WindowInsetsCompat.Type.ime())
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""

                clearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                if (query.length < 3) {
                    showEmptyState()
                    return
                }
                searchRunnable = Runnable { performSearch(query) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
        })

        clearButton.visibility = if (searchInput.text.isNotEmpty()) View.VISIBLE else View.GONE
        showEmptyState()

        findViewById<TextView>(R.id.short_list_gag)
            .addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val tv = v as TextView
                if (tv.text.isNotEmpty()) {
                    tv.post {
                        val layout = tv.layout ?: return@post
                        val textHeight = layout.getLineBottom(tv.lineCount - 1)
                        val availHeight = tv.height - tv.paddingTop - tv.paddingBottom
                        tv.visibility = if (textHeight > availHeight) View.INVISIBLE else View.VISIBLE
                    }
                }
            }

        findViewById<TextView>(R.id.nominatim_attribution).movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showEmptyState() {
        findViewById<View>(R.id.results_container).visibility = View.GONE
        findViewById<View>(R.id.empty_view).visibility = View.VISIBLE
        findViewById<LinearProgressIndicator>(R.id.progress_bar).visibility = View.INVISIBLE
        findViewById<TextView>(R.id.status_text).visibility = View.GONE
    }

    private fun performSearch(query: String) {
        val progressBar = findViewById<LinearProgressIndicator>(R.id.progress_bar)
        val statusText = findViewById<View>(R.id.status_text)
        val resultsContainer = findViewById<View>(R.id.results_container)
        val resultsList = findViewById<ListView>(R.id.results_list)
        val emptyView = findViewById<View>(R.id.empty_view)
        val shortListGag = findViewById<TextView>(R.id.short_list_gag)

        progressBar.visibility = View.VISIBLE
        statusText.visibility = View.GONE
        emptyView.visibility = View.GONE
        resultsContainer.visibility = View.GONE

        Thread {
            val results = NominatimApi.search(query)

            runOnUiThread {
                progressBar.visibility = View.INVISIBLE

                if (results.isEmpty()) {
                    statusText.visibility = View.VISIBLE
                    return@runOnUiThread
                }

                resultsContainer.visibility = View.VISIBLE
                val gagText = arrayOf(
                    R.string.search_short_list_gag_1,
                    R.string.search_short_list_gag_2,
                    R.string.search_short_list_gag_3,
                    R.string.search_short_list_gag_4,
                    R.string.search_short_list_gag_5,
                    R.string.search_short_list_gag_6,
                ).let { ids -> getString(ids[Random.nextInt(ids.size)]) }
                shortListGag.text = gagText
                shortListGag.visibility = View.VISIBLE

                val displayKey: (NominatimApi.GeocodingResult) -> String = { r ->
                    listOfNotNull(r.name, r.city, r.state, r.type, r.country)
                        .joinToString(" | ").lowercase()
                }
                val deduped = results.distinctBy(displayKey)

                val adapter = object : ArrayAdapter<NominatimApi.GeocodingResult>(
                    this@WeatherConfigureActivity,
                    R.layout.item_location_result,
                    R.id.text_title,
                    deduped
                ) {
                    override fun getView(
                        position: Int,
                        convertView: View?,
                        parent: ViewGroup
                    ): View {
                        val view = convertView ?: LayoutInflater.from(context)
                            .inflate(R.layout.item_location_result, parent, false)

                        val item = getItem(position)
                        val title = view.findViewById<TextView>(R.id.text_title)
                        val container = view.findViewById<View>(R.id.item_card)
                        val subtitle = view.findViewById<TextView>(R.id.text_subtitle)

                        title.text = item?.name?.takeUnless { it.isBlank() }
                            ?: item?.displayName?.split(",")?.firstOrNull()?.trim()
                            ?: ""

                        subtitle.text = buildString {
                            val hierarchy = listOfNotNull(
                                item?.city?.takeIf { it != item.name },
                                item?.state,
                                item?.country,
                            )
                            if (hierarchy.isNotEmpty()) {
                                append(hierarchy.joinToString(", "))
                            }
                            item?.type?.let { t ->
                                if (isNotEmpty()) append(" ")
                                append("(${t.replaceFirstChar { it.uppercase() }})")
                            }
                        }

                        container.setOnClickListener {
                            getItem(position)?.let { onLocationSelected(it) }
                        }

                        return view
                    }
                }

                resultsList.adapter = adapter
            }
        }.apply { name = "nominatim-search" }.start()
    }

    private fun onLocationSelected(result: NominatimApi.GeocodingResult) {
        WidgetConfig.save(
            this, appWidgetId,
            WidgetConfig.LocationConfig(
                lat = result.lat,
                lon = result.lon,
                displayName = result.displayName
            )
        )

        setResult(RESULT_OK, Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        })

        val refreshIntent = Intent(this, WeatherPillWidget::class.java).apply {
            action = WeatherPillWidget.ACTION_SILENT_REFRESH
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        sendBroadcast(refreshIntent)

        finish()
    }
}