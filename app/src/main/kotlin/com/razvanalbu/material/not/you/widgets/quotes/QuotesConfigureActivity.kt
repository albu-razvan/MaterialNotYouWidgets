package com.razvanalbu.material.not.you.widgets.quotes

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.marginBottom
import androidx.core.view.marginTop
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.razvanalbu.material.not.you.widgets.R
import com.razvanalbu.material.not.you.widgets.core.BaseConfigureActivity

class QuotesConfigureActivity : BaseConfigureActivity() {

    override val layoutResId = R.layout.activity_quotes_configure

    private val quotes = mutableListOf<Quote>()
    private var editingIndex: Int? = null
    private var doneButtonHeight = 0
    private lateinit var scrollContainer: ViewGroup
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        quotes.addAll(QuotesStore.loadQuotes(this, appWidgetId))

        val quoteInput = findViewById<EditText>(R.id.quote_input)
        val authorInput = findViewById<EditText>(R.id.author_input)
        val addButton = findViewById<MaterialButton>(R.id.add_button)
        val errorText = findViewById<TextView>(R.id.error_text)

        val listView = findViewById<ListView>(R.id.quotes_list)
        listView.emptyView = findViewById(R.id.empty_state)

        adapter = object : BaseAdapter() {
            override fun getCount() = quotes.size
            override fun getItem(pos: Int) = quotes[pos]
            override fun getItemId(pos: Int) = pos.toLong()

            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val view = (convertView as? MaterialCardView
                    ?: LayoutInflater.from(this@QuotesConfigureActivity)
                        .inflate(R.layout.item_quote, parent, false) as MaterialCardView)
                val quote = getItem(pos) as Quote
                view.findViewById<TextView>(R.id.quote_text).text = quote.text
                view.findViewById<TextView>(R.id.quote_author).text = quote.author

                val isEditing = editingIndex == pos
                view.strokeWidth = if (isEditing) {
                    resources.getDimensionPixelSize(R.dimen.quote_edit_stroke)
                } else {
                    0
                }

                view.setOnClickListener {
                    selectQuote(pos)
                }

                view.findViewById<View>(R.id.delete_button).setOnClickListener {
                    val editingIdx = editingIndex
                    if (editingIdx != null) {
                        if (editingIdx == pos) {
                            editingIndex = null
                        } else if (editingIdx > pos) {
                            editingIndex = editingIdx - 1
                        }
                    }
                    quotes.removeAt(pos)
                    QuotesStore.saveQuotes(this@QuotesConfigureActivity, appWidgetId, quotes)
                    notifyDataSetChanged()
                }
                return view
            }
        }
        listView.adapter = adapter

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = quoteInput.text.toString().trim()
                val author = authorInput.text.toString().trim()

                val textTooLong = text.length > 175
                val authorTooLong = author.length > 20
                val tooManyLines = quoteInput.lineCount > 4

                addButton.isEnabled = text.isNotBlank() && author.isNotBlank()
                        && !textTooLong && !authorTooLong && !tooManyLines

                if (textTooLong || tooManyLines) {
                    errorText.text = getString(R.string.quote_too_long)
                    errorText.visibility = View.VISIBLE
                } else if (authorTooLong) {
                    errorText.text = getString(R.string.author_too_long)
                    errorText.visibility = View.VISIBLE
                } else {
                    errorText.visibility = View.GONE
                }
            }
        }

        quoteInput.addTextChangedListener(textWatcher)
        authorInput.addTextChangedListener(textWatcher)

        addButton.isEnabled = false
        addButton.setOnClickListener { addQuote() }

        findViewById<MaterialButton>(R.id.done_button).setOnClickListener { done() }

        val imageView = findViewById<View>(R.id.empty_state_shape)

        val animator = ObjectAnimator.ofFloat(
            imageView,
            View.ROTATION,
            0f,
            360f
        ).apply {
            duration = 30000
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
        }

        animator.start()
    }

    override fun setupWindowInsets() {
        val rootContent = findViewById<ViewGroup>(android.R.id.content)
        val layoutRoot = findViewById<ViewGroup>(R.id.root_layout)
        scrollContainer = findViewById(R.id.scroll_container)
        val doneButton = findViewById<View>(R.id.done_button)

        rootContent.clipToPadding = false
        rootContent.clipChildren = false
        layoutRoot.clipToPadding = false
        layoutRoot.clipChildren = false

        doneButton.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            doneButtonHeight = doneButton.height + doneButton.marginTop + doneButton.marginBottom
            val insets = ViewCompat.getRootWindowInsets(scrollContainer)

            val systemBars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

            scrollContainer.updatePadding(bottom = 0.coerceAtLeast(ime - systemBars - doneButtonHeight))
        }

        ViewCompat.setOnApplyWindowInsetsListener(layoutRoot) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )

            if (!isImeAnimating) {
                scrollContainer.updatePadding(
                    bottom = 0.coerceAtLeast(ime.bottom - systemBars.bottom - doneButtonHeight)
                )
            }

            insets
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            scrollContainer,
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
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

                    scrollContainer.updatePadding(
                        bottom = 0.coerceAtLeast(ime.bottom - systemBars.bottom - doneButtonHeight)
                    )

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

        val isNight =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !isNight
            controller.isAppearanceLightNavigationBars = !isNight
        }
    }

    private fun selectQuote(pos: Int) {
        val quoteInput = findViewById<EditText>(R.id.quote_input)
        val authorInput = findViewById<EditText>(R.id.author_input)
        val addButton = findViewById<MaterialButton>(R.id.add_button)

        if (editingIndex == pos) {
            editingIndex = null
            quoteInput.text.clear()
            authorInput.text.clear()
            addButton.setIconResource(R.drawable.ic_add_quote)
        } else {
            editingIndex = pos
            val quote = quotes[pos]
            quoteInput.setText(quote.text)
            authorInput.setText(quote.author)
            addButton.setIconResource(R.drawable.ic_confirm_edit)
        }
        adapter.notifyDataSetChanged()
    }

    private fun addQuote() {
        val quoteInput = findViewById<EditText>(R.id.quote_input)
        val authorInput = findViewById<EditText>(R.id.author_input)
        val addButton = findViewById<MaterialButton>(R.id.add_button)
        val errorText = findViewById<TextView>(R.id.error_text)

        val text = quoteInput.text.toString().trim()
        val author = authorInput.text.toString().trim()

        if (text.isEmpty() || author.isEmpty()) {
            return
        }

        if (text.length > 175 || quoteInput.lineCount > 4) {
            errorText.text = getString(R.string.quote_too_long)
            errorText.visibility = View.VISIBLE

            return
        }

        if (author.length > 20) {
            errorText.text = getString(R.string.author_too_long)
            errorText.visibility = View.VISIBLE

            return
        }

        val idx = editingIndex
        if (idx != null) {
            quotes[idx] = Quote(text, author)
            editingIndex = null
        } else {
            quotes.add(Quote(text, author))
        }
        QuotesStore.saveQuotes(this, appWidgetId, quotes)

        addButton.setIconResource(R.drawable.ic_add_quote)

        quoteInput.text.clear()
        authorInput.text.clear()
        quoteInput.clearFocus()
        authorInput.clearFocus()

        adapter.notifyDataSetChanged()

        if (quotes.size == 1) {
            QuotesStore.pickRandomQuote(this, appWidgetId)
        }
    }

    private fun done() {
        if (quotes.isNotEmpty()) {
            val refreshIntent = Intent(this, QuotesWidgetProvider::class.java).apply {
                action = QuotesWidgetProvider.ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            sendBroadcast(refreshIntent)
        }

        setResult(RESULT_OK, Intent())

        finish()
    }
}
