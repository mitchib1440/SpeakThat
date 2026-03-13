package com.micoyc.speakthat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "FilterBottomSheet"
        private const val ARG_APP_NAME = "arg_app_name"
        private const val ARG_PACKAGE_NAME = "arg_package_name"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_TEXT = "arg_text"
        private const val ARG_TIMESTAMP = "arg_timestamp"

        fun newInstance(notification: NotificationReaderService.NotificationData): FilterBottomSheetFragment {
            return FilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_APP_NAME, notification.appName)
                    putString(ARG_PACKAGE_NAME, notification.packageName)
                    putString(ARG_TITLE, notification.title)
                    putString(ARG_TEXT, notification.text)
                    putString(ARG_TIMESTAMP, notification.timestamp)
                }
            }
        }
    }

    private enum class FilterType { SMART, EXACT, APP }

    private var selectedFilterType = FilterType.SMART

    private lateinit var radioSmart: RadioButton
    private lateinit var radioExact: RadioButton
    private lateinit var radioApp: RadioButton
    private lateinit var sectionFilterPreview: View
    private lateinit var textFilterPreview: TextView
    private lateinit var checkPrivate: CheckBox
    private lateinit var buttonCreateFilter: MaterialButton

    private var appName = ""
    private var packageName = ""
    private var notifTitle = ""
    private var notifText = ""
    private var timestamp = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_filter_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appName = arguments?.getString(ARG_APP_NAME, "") ?: ""
        packageName = arguments?.getString(ARG_PACKAGE_NAME, "") ?: ""
        notifTitle = arguments?.getString(ARG_TITLE, "") ?: ""
        notifText = arguments?.getString(ARG_TEXT, "") ?: ""
        timestamp = arguments?.getString(ARG_TIMESTAMP, "") ?: ""

        bindViews(view)
        populateHeader(view)
        setupRadioOptions(view)
        setupPrivateCheckbox(view)
        setupCreateButton()
        handleInsets(view)

        updatePreview()
    }

    private fun bindViews(view: View) {
        radioSmart = view.findViewById(R.id.radioSmart)
        radioExact = view.findViewById(R.id.radioExact)
        radioApp = view.findViewById(R.id.radioApp)
        sectionFilterPreview = view.findViewById(R.id.sectionFilterPreview)
        textFilterPreview = view.findViewById(R.id.textFilterPreview)
        checkPrivate = view.findViewById(R.id.checkPrivate)
        buttonCreateFilter = view.findViewById(R.id.buttonCreateFilter)
    }

    private fun populateHeader(view: View) {
        val appIcon: ImageView = view.findViewById(R.id.sheetAppIcon)
        val appNameTime: TextView = view.findViewById(R.id.sheetAppNameTime)
        val titleView: TextView = view.findViewById(R.id.sheetNotificationTitle)
        val bodyView: TextView = view.findViewById(R.id.sheetNotificationBody)

        val displayTimestamp = formatTimestamp(timestamp)
        appNameTime.text = "$appName \u2022 $displayTimestamp"

        if (notifTitle.isNotEmpty()) {
            titleView.text = notifTitle
            titleView.visibility = View.VISIBLE
            bodyView.text = stripTitlePrefix(notifTitle, notifText)
        } else {
            titleView.visibility = View.GONE
            bodyView.text = notifText
        }

        val icon = loadAppIcon(requireContext(), packageName)
        if (icon != null) {
            appIcon.setImageDrawable(icon)
        } else {
            appIcon.setImageResource(R.drawable.speakthaticon)
        }

        val appOptionTitle: TextView = view.findViewById(R.id.textAppOptionTitle)
        appOptionTitle.text = getString(R.string.filter_sheet_app_title, appName)

        titleView.customSelectionActionModeCallback = createSelectionCallback(titleView)
        bodyView.customSelectionActionModeCallback = createSelectionCallback(bodyView)
    }

    private fun setupRadioOptions(view: View) {
        val optionSmart: View = view.findViewById(R.id.optionSmart)
        val optionExact: View = view.findViewById(R.id.optionExact)
        val optionApp: View = view.findViewById(R.id.optionApp)

        optionSmart.setOnClickListener { selectFilterType(FilterType.SMART) }
        optionExact.setOnClickListener { selectFilterType(FilterType.EXACT) }
        optionApp.setOnClickListener { selectFilterType(FilterType.APP) }
    }

    private fun selectFilterType(type: FilterType) {
        selectedFilterType = type
        radioSmart.isChecked = type == FilterType.SMART
        radioExact.isChecked = type == FilterType.EXACT
        radioApp.isChecked = type == FilterType.APP
        updatePreview()
    }

    private fun updatePreview() {
        when (selectedFilterType) {
            FilterType.SMART -> {
                sectionFilterPreview.visibility = View.VISIBLE
                textFilterPreview.text = generatePatternMatch(notifText)
            }
            FilterType.EXACT -> {
                sectionFilterPreview.visibility = View.VISIBLE
                textFilterPreview.text = notifText
            }
            FilterType.APP -> {
                sectionFilterPreview.visibility = View.GONE
            }
        }
    }

    private fun setupPrivateCheckbox(view: View) {
        val optionPrivate: View = view.findViewById(R.id.optionPrivate)
        optionPrivate.setOnClickListener {
            checkPrivate.isChecked = !checkPrivate.isChecked
        }
    }

    private fun setupCreateButton() {
        buttonCreateFilter.setOnClickListener {
            val isPrivate = checkPrivate.isChecked
            val ctx = requireContext()
            val filterType = selectedFilterType

            val addedFilterRule: String? = when (filterType) {
                FilterType.SMART -> generatePatternMatch(notifText).also { addToWordFilter(ctx, it, isPrivate) }
                FilterType.EXACT -> notifText.also { addToWordFilter(ctx, it, isPrivate) }
                FilterType.APP -> { addToAppFilter(ctx, packageName, isPrivate); null }
            }

            InAppLogger.logUserAction("Filter created via bottom sheet",
                "Type: ${filterType.name}, Private: $isPrivate, App: $appName")

            dismiss()

            val actionWord = if (isPrivate) {
                getString(R.string.filter_sheet_toast_private)
            } else {
                getString(R.string.filter_sheet_toast_blocked)
            }
            val message = getString(R.string.filter_sheet_toast_created, actionWord)

            val rootView = requireActivity().findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setAction(R.string.undo_action) {
                    when (filterType) {
                        FilterType.SMART, FilterType.EXACT -> {
                            addedFilterRule?.let { rule -> removeFromWordFilter(ctx, rule, isPrivate) }
                        }
                        FilterType.APP -> removeFromAppFilter(ctx, packageName, isPrivate)
                    }
                    InAppLogger.logUserAction("Filter undone via snackbar",
                        "Type: ${filterType.name}, Private: $isPrivate, App: $appName")
                }

            val navBarInset = ViewCompat.getRootWindowInsets(rootView)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val snackView = snackbar.view
            val params = snackView.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.bottomMargin = navBarInset
                snackView.layoutParams = params
            }

            snackbar.show()
        }
    }

    private fun handleInsets(view: View) {
        val buttonContainer: View = view.findViewById(R.id.buttonContainer)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            buttonContainer.setPadding(
                buttonContainer.paddingLeft,
                buttonContainer.paddingTop,
                buttonContainer.paddingRight,
                16.dpToPx() + systemBars.bottom
            )
            insets
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    // -- Filter persistence (mirrors DevelopmentSettingsActivity logic) --

    private fun addToWordFilter(context: Context, filterRule: String, isPrivate: Boolean) {
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val prefKey = if (isPrivate) "word_blacklist_private" else "word_blacklist"
        val currentFilters = HashSet(prefs.getStringSet(prefKey, HashSet()) ?: HashSet())
        currentFilters.add(filterRule)
        prefs.edit().putStringSet(prefKey, currentFilters).apply()

        Log.d(TAG, "Added word filter: '$filterRule' (private=$isPrivate)")
        InAppLogger.log("FilterSheet", "Word filter added: '$filterRule' (private=$isPrivate)")
    }

    private fun addToAppFilter(context: Context, pkg: String, isPrivate: Boolean) {
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val prefKey = if (isPrivate) "app_private_flags" else "app_list"
        val currentFilters = HashSet(prefs.getStringSet(prefKey, HashSet()) ?: HashSet())
        currentFilters.add(pkg)

        val editor = prefs.edit()
        editor.putStringSet(prefKey, currentFilters)
        if (!isPrivate) {
            editor.putString("app_list_mode", "blacklist")
        }
        editor.apply()

        Log.d(TAG, "Added app filter: '$pkg' (private=$isPrivate)")
        InAppLogger.log("FilterSheet", "App filter added: '$pkg' (private=$isPrivate)")
    }

    private fun removeFromWordFilter(context: Context, filterRule: String, isPrivate: Boolean) {
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val prefKey = if (isPrivate) "word_blacklist_private" else "word_blacklist"
        val currentFilters = HashSet(prefs.getStringSet(prefKey, HashSet()) ?: HashSet())
        currentFilters.remove(filterRule)
        prefs.edit().putStringSet(prefKey, currentFilters).apply()

        Log.d(TAG, "Removed word filter (undo): '$filterRule' (private=$isPrivate)")
        InAppLogger.log("FilterSheet", "Word filter undone: '$filterRule' (private=$isPrivate)")
    }

    private fun removeFromAppFilter(context: Context, pkg: String, isPrivate: Boolean) {
        val prefs = context.getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val prefKey = if (isPrivate) "app_private_flags" else "app_list"
        val currentFilters = HashSet(prefs.getStringSet(prefKey, HashSet()) ?: HashSet())
        currentFilters.remove(pkg)
        prefs.edit().putStringSet(prefKey, currentFilters).apply()

        Log.d(TAG, "Removed app filter (undo): '$pkg' (private=$isPrivate)")
        InAppLogger.log("FilterSheet", "App filter undone: '$pkg' (private=$isPrivate)")
    }

    // -- Pattern generation (user-specified Unicode-aware logic) --

    private fun generatePatternMatch(text: String): String {
        var pattern = text

        // 1. Remove URLs
        pattern = pattern.replace(Regex("(?i)https?://[^\\s]+"), "[URL]")

        // 2. Remove Email Addresses
        pattern = pattern.replace(Regex("(?i)[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), "[EMAIL]")

        // 3. Remove Times (14:30, 2:30, 14.30, optional AM/PM)
        pattern = pattern.replace(Regex("(?i)\\b\\d{1,2}[:.]\\d{2}(?:[:.]\\d{2})?(?:\\s*[ap]m)?\\b"), "[TIME]")

        // 4. Remove Dates (YYYY-MM-DD, DD/MM/YY, etc.)
        pattern = pattern.replace(Regex("\\b\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}\\b"), "[DATE]")

        // 5. Remove Standalone Numbers
        pattern = pattern.replace(Regex("\\b\\d+\\b"), "[NUMBER]")

        // 6. Remove file sizes & percentages
        pattern = pattern.replace(Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:mb|gb|kb|tb|%)\\b"), "[SIZE]")

        // 7. Clean up multiple spaces
        pattern = pattern.replace(Regex("\\s+"), " ").trim()

        return pattern
    }

    // -- Custom text selection menu --

    private fun createSelectionCallback(textView: TextView): ActionMode.Callback {
        return object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                menu.add(0, 1, 0, "Copy")
                menu.add(0, 2, 1, "Create filter")
                menu.add(0, 3, 2, "Create swap")
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val start = textView.selectionStart
                val end = textView.selectionEnd
                if (start < 0 || end <= start) {
                    mode.finish()
                    return true
                }
                val selected = textView.text.subSequence(start, end).toString()

                when (item.itemId) {
                    1 -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("SpeakThat", selected))
                        Toast.makeText(requireContext(), "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        mode.finish()
                    }
                    2 -> {
                        val intent = Intent(requireContext(), FilterSettingsActivity::class.java).apply {
                            putExtra("extra_prefill_text", selected)
                            putExtra("extra_target_section", "word_filters")
                        }
                        startActivity(intent)
                        mode.finish()
                        dismiss()
                    }
                    3 -> {
                        val intent = Intent(requireContext(), FilterSettingsActivity::class.java).apply {
                            putExtra("extra_prefill_text", selected)
                            putExtra("extra_target_section", "word_swaps")
                        }
                        startActivity(intent)
                        mode.finish()
                        dismiss()
                    }
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    // -- Utility --

    private fun formatTimestamp(ts: String): String {
        return try {
            if (ts.length >= 16) ts.substring(11, 16) else ts
        } catch (e: Exception) {
            ts
        }
    }

    private fun stripTitlePrefix(title: String, text: String): String {
        val prefix = "$title: "
        return if (text.startsWith(prefix, ignoreCase = true)) {
            text.removePrefix(prefix).trimStart()
        } else {
            text
        }
    }

    private fun loadAppIcon(context: Context, pkg: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load icon for $pkg: ${e.message}")
            null
        }
    }
}
