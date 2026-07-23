package com.razvanalbu.material.not.you.widgets.core

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.razvanalbu.material.not.you.widgets.quotes.QuotesConfigureActivity
import com.razvanalbu.material.not.you.widgets.weather.WeatherConfigureActivity

class WidgetConfigProxyActivity : AppCompatActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()

            return
        }

        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
        )

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }

        val configClass = BaseWidgetProvider.resolveConfigurationActivity(this, appWidgetId)
            ?: return

        startActivity(
            Intent(this, configClass).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
        )
    }
}
