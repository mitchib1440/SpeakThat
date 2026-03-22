/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * SpeakThat! Copyright © Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class ClockSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var switchClockEnabled: MaterialSwitch
    private lateinit var switchClockPrecision: MaterialSwitch
    private lateinit var radioGroupClockInterval: RadioGroup
    private lateinit var editClockTemplate: TextInputEditText

    private var isApplyingUiState = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clock_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.settings_clock_title)

        prefs = getSharedPreferences(NotificationReaderService.PREFS_NAME, MODE_PRIVATE)
        switchClockEnabled = findViewById(R.id.switchClockEnabled)
        switchClockPrecision = findViewById(R.id.switchClockPrecision)
        radioGroupClockInterval = findViewById(R.id.radioGroupClockInterval)
        editClockTemplate = findViewById(R.id.editClockTemplate)

        loadState()
        setupInteractions()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun coerceInterval(raw: Int): Int {
        return when (raw) {
            15, 30, 60, NotificationReaderService.SPEAKTHAT_CLOCK_INTERVAL_3_HOURS_MINUTES -> raw
            else -> NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_INTERVAL_MINUTES
        }
    }

    private fun loadState() {
        isApplyingUiState = true
        switchClockEnabled.isChecked = prefs.getBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_ENABLED, false)
        switchClockPrecision.isChecked =
            prefs.getBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_PRECISION_MODE, false)
        val interval = coerceInterval(
            prefs.getInt(
                NotificationReaderService.PREF_SPEAKTHAT_CLOCK_INTERVAL_MINUTES,
                NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_INTERVAL_MINUTES
            )
        )
        val radioId = when (interval) {
            15 -> R.id.radioClockInterval15
            30 -> R.id.radioClockInterval30
            60 -> R.id.radioClockInterval60
            else -> R.id.radioClockInterval180
        }
        radioGroupClockInterval.check(radioId)
        val template = prefs.getString(
            NotificationReaderService.PREF_SPEAKTHAT_CLOCK_TEMPLATE,
            NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_TEMPLATE
        ) ?: NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_TEMPLATE
        editClockTemplate.setText(template)
        isApplyingUiState = false
    }

    override fun onPause() {
        super.onPause()
        saveClockTemplateFromField()
    }

    private fun saveClockTemplateFromField() {
        val trimmed = editClockTemplate.text?.toString()?.trim().orEmpty()
        val toStore = if (trimmed.isEmpty()) {
            NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_TEMPLATE
        } else {
            trimmed
        }
        prefs.edit().putString(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_TEMPLATE, toStore).apply()
        if (trimmed.isEmpty()) {
            editClockTemplate.setText(NotificationReaderService.DEFAULT_SPEAKTHAT_CLOCK_TEMPLATE)
        }
    }

    private fun setupInteractions() {
        switchClockEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingUiState) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_ENABLED, isChecked).apply()
        }

        switchClockPrecision.setOnCheckedChangeListener { _, isChecked ->
            if (isApplyingUiState) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_PRECISION_MODE, isChecked).apply()
        }

        radioGroupClockInterval.setOnCheckedChangeListener { _, checkedId ->
            if (isApplyingUiState) return@setOnCheckedChangeListener
            val minutes = when (checkedId) {
                R.id.radioClockInterval15 -> 15
                R.id.radioClockInterval30 -> 30
                R.id.radioClockInterval60 -> 60
                R.id.radioClockInterval180 -> NotificationReaderService.SPEAKTHAT_CLOCK_INTERVAL_3_HOURS_MINUTES
                else -> return@setOnCheckedChangeListener
            }
            prefs.edit().putInt(NotificationReaderService.PREF_SPEAKTHAT_CLOCK_INTERVAL_MINUTES, minutes).apply()
        }
    }
}
