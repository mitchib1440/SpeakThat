package com.micoyc.speakthat.summary

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.micoyc.speakthat.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SummarySettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    private lateinit var globalSummarySwitch: SwitchCompat
    private lateinit var schedulerSwitch: SwitchCompat
    private lateinit var overlayPermissionButton: MaterialButton
    private lateinit var overlayPermissionStatus: TextView
    private lateinit var scheduleTimeRow: LinearLayout
    private lateinit var selectedScheduleTime: TextView
    private lateinit var greetingNameInput: TextInputEditText
    private lateinit var speechPacingSeekBar: SeekBar
    private lateinit var speechPacingValue: TextView
    private lateinit var notificationOrderSpinner: Spinner
    private lateinit var summaryActionTriggerRow: LinearLayout
    private lateinit var automationPackageRow: LinearLayout

    private var selectedHour = DEFAULT_HOUR
    private var selectedMinute = DEFAULT_MINUTE
    private var isApplyingUiState = false
    private var hasInitializedNotificationOrderSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.settings_summary_title)

        prefs = SummarySettingsGate.prefs(this)

        bindViews()
        loadState()
        setupInteractions()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayPermissionUi()
    }

    private fun bindViews() {
        globalSummarySwitch = findViewById(R.id.switchEnableSummary)
        schedulerSwitch = findViewById(R.id.switchEnableScheduler)
        overlayPermissionButton = findViewById(R.id.btnGrantOverlayPermission)
        overlayPermissionStatus = findViewById(R.id.tvOverlayPermissionStatus)
        scheduleTimeRow = findViewById(R.id.rowScheduleTime)
        selectedScheduleTime = findViewById(R.id.tvSelectedScheduleTime)
        greetingNameInput = findViewById(R.id.etGreetingName)
        speechPacingSeekBar = findViewById(R.id.seekSpeechPacing)
        speechPacingValue = findViewById(R.id.tvSpeechPacingValue)
        notificationOrderSpinner = findViewById(R.id.spinnerNotificationOrder)
        summaryActionTriggerRow = findViewById(R.id.cardSummaryStringActionTrigger)
        automationPackageRow = findViewById(R.id.cardAutomationStringPackage)
    }

    private fun loadState() {
        selectedHour = prefs.getInt(SummaryConstants.KEY_HOUR_OF_DAY, DEFAULT_HOUR)
        selectedMinute = prefs.getInt(SummaryConstants.KEY_MINUTE, DEFAULT_MINUTE)
        selectedScheduleTime.text = formatTime(selectedHour, selectedMinute)

        val greetingName = prefs.getString(SummaryConstants.KEY_GREETING_NAME, SummaryConstants.DEFAULT_GREETING_NAME)
            ?: SummaryConstants.DEFAULT_GREETING_NAME
        greetingNameInput.setText(greetingName)

        val pauseSeconds = prefs.getInt(SummaryConstants.KEY_PAUSE_SECONDS, DEFAULT_PAUSE_SECONDS).coerceIn(0, 5)
        speechPacingSeekBar.progress = pauseSeconds
        updatePacingLabel(pauseSeconds)
        setupNotificationOrderSpinner()

        setSwitchCheckedSilently(
            globalSummarySwitch,
            prefs.getBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false)
        )
        setSwitchCheckedSilently(
            schedulerSwitch,
            prefs.getBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false)
        )
        refreshOverlayPermissionUi()
    }

    private fun setupInteractions() {
        overlayPermissionButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        summaryActionTriggerRow.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText("SpeakThat automation", SummaryConstants.ACTION_TRIGGER_SUMMARY)
            )
            Toast.makeText(
                this,
                getString(R.string.rules_quick_strings_copied, getString(R.string.summary_action_start_title)),
                Toast.LENGTH_SHORT
            ).show()
        }

        automationPackageRow.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val packageValue = getString(R.string.rules_quick_strings_package_value)
            clipboard.setPrimaryClip(ClipData.newPlainText("SpeakThat automation", packageValue))
            Toast.makeText(
                this,
                getString(R.string.rules_quick_strings_copied, getString(R.string.rules_quick_strings_package_label)),
                Toast.LENGTH_SHORT
            ).show()
        }

        globalSummarySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingUiState) {
                return@setOnCheckedChangeListener
            }

            prefs.edit().putBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, isChecked).apply()
            if (!isChecked) {
                SummaryScheduler.cancel(this)
            } else if (SummarySettingsGate.canSchedule(this)) {
                SummaryScheduler.schedule(this)
            }
            refreshOverlayPermissionUi()
        }

        schedulerSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingUiState) {
                return@setOnCheckedChangeListener
            }

            prefs.edit().putBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, isChecked).apply()
            if (isChecked) {
                SummaryScheduler.schedule(this)
            } else {
                SummaryScheduler.cancel(this)
            }
            refreshOverlayPermissionUi()
        }

        scheduleTimeRow.setOnClickListener {
            if (!scheduleTimeRow.isEnabled) {
                return@setOnClickListener
            }
            android.app.TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    selectedScheduleTime.text = formatTime(selectedHour, selectedMinute)
                    prefs.edit()
                        .putInt(SummaryConstants.KEY_HOUR_OF_DAY, selectedHour)
                        .putInt(SummaryConstants.KEY_MINUTE, selectedMinute)
                        .apply()
                    if (SummarySettingsGate.canSchedule(this)) {
                        SummaryScheduler.schedule(this)
                    }
                },
                selectedHour,
                selectedMinute,
                false
            ).show()
        }

        greetingNameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val name = s?.toString()?.trim().orEmpty()
                prefs.edit()
                    .putString(
                        SummaryConstants.KEY_GREETING_NAME,
                        if (name.isBlank()) SummaryConstants.DEFAULT_GREETING_NAME else name
                    )
                    .apply()
            }
        })

        speechPacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress.coerceIn(0, 5)
                updatePacingLabel(seconds)
                prefs.edit().putInt(SummaryConstants.KEY_PAUSE_SECONDS, seconds).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        notificationOrderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (isApplyingUiState) {
                    return
                }
                if (!hasInitializedNotificationOrderSelection) {
                    hasInitializedNotificationOrderSelection = true
                    return
                }

                val selectedOrder = when (position) {
                    0 -> SummaryConstants.ORDER_OLDEST_FIRST
                    else -> SummaryConstants.ORDER_NEWEST_FIRST
                }
                prefs.edit().putString(SummaryConstants.KEY_NOTIFICATION_ORDER, selectedOrder).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshOverlayPermissionUi() {
        val granted = SummarySettingsGate.isOverlayPermissionGranted(this)
        if (!granted) {
            prefs.edit().putBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false).apply()
            SummaryScheduler.cancel(this)
        }

        val globalEnabled = prefs.getBoolean(SummaryConstants.KEY_GLOBAL_ENABLED, false)
        val schedulerEnabled = prefs.getBoolean(SummaryConstants.KEY_SCHEDULER_ENABLED, false)
        val schedulerControlsEnabled = granted && globalEnabled

        setSwitchCheckedSilently(globalSummarySwitch, globalEnabled)
        setSwitchCheckedSilently(schedulerSwitch, schedulerEnabled)

        overlayPermissionButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        overlayPermissionStatus.text = if (granted) {
            getString(R.string.summary_settings_permission_granted)
        } else {
            getString(R.string.summary_settings_permission_subtitle)
        }

        globalSummarySwitch.isEnabled = granted
        globalSummarySwitch.alpha = if (granted) 1f else 0.45f

        schedulerSwitch.isEnabled = schedulerControlsEnabled
        schedulerSwitch.alpha = if (schedulerControlsEnabled) 1f else 0.45f
        scheduleTimeRow.isEnabled = schedulerControlsEnabled
        scheduleTimeRow.isClickable = schedulerControlsEnabled
        scheduleTimeRow.alpha = if (schedulerControlsEnabled) 1f else 0.45f

        if (schedulerControlsEnabled && schedulerEnabled) {
            SummaryScheduler.schedule(this)
        }
    }

    private fun setSwitchCheckedSilently(switch: SwitchCompat, value: Boolean) {
        isApplyingUiState = true
        switch.isChecked = value
        isApplyingUiState = false
    }

    private fun updatePacingLabel(seconds: Int) {
        speechPacingValue.text = getString(R.string.summary_settings_pacing_value_format, seconds)
    }

    private fun setupNotificationOrderSpinner() {
        val options = arrayOf(
            getString(R.string.summary_settings_order_oldest_first),
            getString(R.string.summary_settings_order_newest_first)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        notificationOrderSpinner.adapter = adapter

        val order = prefs.getString(
            SummaryConstants.KEY_NOTIFICATION_ORDER,
            SummaryConstants.ORDER_NEWEST_FIRST
        ) ?: SummaryConstants.ORDER_NEWEST_FIRST
        val selectedIndex = if (order == SummaryConstants.ORDER_OLDEST_FIRST) 0 else 1
        setSpinnerSelectionSilently(notificationOrderSpinner, selectedIndex)
    }

    private fun setSpinnerSelectionSilently(spinner: Spinner, index: Int) {
        isApplyingUiState = true
        spinner.setSelection(index, false)
        isApplyingUiState = false
        hasInitializedNotificationOrderSelection = false
    }

    private fun formatTime(hourOfDay: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(calendar.timeInMillis))
    }

    companion object {
        private const val DEFAULT_HOUR = 8
        private const val DEFAULT_MINUTE = 0
        private const val DEFAULT_PAUSE_SECONDS = 2
    }
}
