package com.micoyc.speakthat.summary

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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

    private var selectedHour = DEFAULT_HOUR
    private var selectedMinute = DEFAULT_MINUTE
    private var isApplyingUiState = false

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
