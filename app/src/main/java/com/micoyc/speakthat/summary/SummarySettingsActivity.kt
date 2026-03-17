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

    private lateinit var enableSummarySwitch: SwitchCompat
    private lateinit var overlayPermissionButton: MaterialButton
    private lateinit var overlayPermissionStatus: TextView
    private lateinit var scheduleTimeRow: LinearLayout
    private lateinit var selectedScheduleTime: TextView
    private lateinit var greetingNameInput: TextInputEditText
    private lateinit var speechPacingSeekBar: SeekBar
    private lateinit var speechPacingValue: TextView

    private var selectedHour = DEFAULT_HOUR
    private var selectedMinute = DEFAULT_MINUTE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_summary_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.settings_summary_title)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        bindViews()
        loadState()
        setupInteractions()
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayPermissionUi()
    }

    private fun bindViews() {
        enableSummarySwitch = findViewById(R.id.switchEnableSummary)
        overlayPermissionButton = findViewById(R.id.btnGrantOverlayPermission)
        overlayPermissionStatus = findViewById(R.id.tvOverlayPermissionStatus)
        scheduleTimeRow = findViewById(R.id.rowScheduleTime)
        selectedScheduleTime = findViewById(R.id.tvSelectedScheduleTime)
        greetingNameInput = findViewById(R.id.etGreetingName)
        speechPacingSeekBar = findViewById(R.id.seekSpeechPacing)
        speechPacingValue = findViewById(R.id.tvSpeechPacingValue)
    }

    private fun loadState() {
        selectedHour = prefs.getInt(KEY_SCHEDULE_HOUR, DEFAULT_HOUR)
        selectedMinute = prefs.getInt(KEY_SCHEDULE_MINUTE, DEFAULT_MINUTE)
        selectedScheduleTime.text = formatTime(selectedHour, selectedMinute)

        val greetingName = prefs.getString(KEY_GREETING_NAME, SummaryConstants.DEFAULT_GREETING_NAME)
            ?: SummaryConstants.DEFAULT_GREETING_NAME
        greetingNameInput.setText(greetingName)

        val pauseSeconds = prefs.getInt(KEY_PAUSE_SECONDS, DEFAULT_PAUSE_SECONDS).coerceIn(0, 5)
        speechPacingSeekBar.progress = pauseSeconds
        updatePacingLabel(pauseSeconds)

        enableSummarySwitch.isChecked = prefs.getBoolean(KEY_ENABLED, false)
    }

    private fun setupInteractions() {
        overlayPermissionButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        enableSummarySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_ENABLED, isChecked).apply()
            if (isChecked) {
                SummaryScheduler.schedule(this)
            } else {
                SummaryScheduler.cancel(this)
            }
        }

        scheduleTimeRow.setOnClickListener {
            android.app.TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    selectedScheduleTime.text = formatTime(selectedHour, selectedMinute)
                    prefs.edit()
                        .putInt(KEY_SCHEDULE_HOUR, selectedHour)
                        .putInt(KEY_SCHEDULE_MINUTE, selectedMinute)
                        .apply()
                    if (enableSummarySwitch.isChecked) {
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
                        KEY_GREETING_NAME,
                        if (name.isBlank()) SummaryConstants.DEFAULT_GREETING_NAME else name
                    )
                    .apply()
            }
        })

        speechPacingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val seconds = progress.coerceIn(0, 5)
                updatePacingLabel(seconds)
                prefs.edit().putInt(KEY_PAUSE_SECONDS, seconds).apply()
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
        val granted = Settings.canDrawOverlays(this)
        overlayPermissionButton.visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        overlayPermissionStatus.text = if (granted) {
            getString(R.string.summary_settings_permission_granted)
        } else {
            getString(R.string.summary_settings_permission_subtitle)
        }

        enableSummarySwitch.isEnabled = granted
        enableSummarySwitch.alpha = if (granted) 1f else 0.45f
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
        private const val PREFS_NAME = "SummarySettings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SCHEDULE_HOUR = "schedule_hour"
        private const val KEY_SCHEDULE_MINUTE = "schedule_minute"
        private const val KEY_GREETING_NAME = "greeting_name"
        private const val KEY_PAUSE_SECONDS = "pause_seconds"

        private const val DEFAULT_HOUR = 8
        private const val DEFAULT_MINUTE = 0
        private const val DEFAULT_PAUSE_SECONDS = 2
    }
}
