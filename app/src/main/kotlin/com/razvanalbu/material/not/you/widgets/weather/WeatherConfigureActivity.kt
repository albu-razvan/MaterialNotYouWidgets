package com.razvanalbu.material.not.you.widgets.weather

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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BaseConfigureActivity
import com.razvanalbu.material.not.you.widgets.weather.providers.NominatimApi
import kotlin.random.Random

class WeatherConfigureActivity : BaseConfigureActivity() {

    override val layoutResId = R.layout.activity_weather_configure

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSearch()
        setupProviderSelector()

        findViewById<EditText>(R.id.search_input).clearFocus()
    }

    override fun onDestroy() {
        if (hasChanged) {
            WeatherWidgetStateManager.scheduleRefresh(this, appWidgetId)
        }

        searchRunnable?.let { searchHandler.removeCallbacks(it) }

        super.onDestroy()
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
                        tv.visibility =
                            if (textHeight > availHeight) View.INVISIBLE else View.VISIBLE
                    }
                }
            }

        findViewById<TextView>(R.id.nominatim_attribution).movementMethod =
            LinkMovementMethod.getInstance()
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

    private fun setupProviderSelector() {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.provider_toggle_group)
        val currentConfig = WidgetConfig.load(this, appWidgetId)
        if (currentConfig != null) {
            toggleGroup.check(
                if (currentConfig.provider == PROVIDER_OPEN_METEO)
                    R.id.provider_openmeteo
                else
                    R.id.provider_metno
            )
        } else {
            toggleGroup.check(R.id.provider_metno)
        }

        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            val provider = if (checkedId == R.id.provider_openmeteo)
                PROVIDER_OPEN_METEO
            else
                PROVIDER_MET_NO

            val config = WidgetConfig.load(this@WeatherConfigureActivity, appWidgetId)
            if (config != null) {
                val updatedConfig = config.copy(provider = provider)
                WidgetConfig.save(this@WeatherConfigureActivity, appWidgetId, updatedConfig)
                hasChanged = true
            }
        }
    }

    private fun onLocationSelected(result: NominatimApi.GeocodingResult) {
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.provider_toggle_group)
        val provider = if (toggleGroup.checkedButtonId == R.id.provider_openmeteo)
            PROVIDER_OPEN_METEO
        else
            PROVIDER_MET_NO

        WidgetConfig.save(
            this, appWidgetId,
            WidgetConfig.LocationConfig(
                lat = result.lat,
                lon = result.lon,
                displayName = result.displayName,
                provider = provider,
            )
        )

        hasChanged = true
        setResult(RESULT_OK, Intent())

        finish()
    }
}