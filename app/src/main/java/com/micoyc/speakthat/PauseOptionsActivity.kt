package com.micoyc.speakthat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.ActivityPauseOptionsBinding
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RuleManager
import java.util.Calendar

class PauseOptionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPauseOptionsBinding
    private lateinit var ruleManager: RuleManager
    private var mode: String = "MASTER"
    private var customDurationMinutes = 60
    private val activeRules = mutableListOf<Rule>()
    private val selectedRuleIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPauseOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ruleManager = RuleManager(this)
        mode = intent.getStringExtra("EXTRA_MODE") ?: "MASTER"

        setupUI()
    }

    private fun setupUI() {
        if (mode == "MASTER") {
            binding.textTitle.text = "Pause SpeakThat"
            binding.layoutRulesContainer.visibility = View.GONE
        } else {
            binding.textTitle.text = "Pause Rules"
            binding.layoutRulesContainer.visibility = View.VISIBLE
            loadActiveRules()
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, 1)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val nextHourFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        binding.radioUntilNextHour.text = "Until ${nextHourFormat.format(calendar.time)}"

        binding.radioGroupDuration.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radioForDuration) {
                binding.layoutDurationPicker.visibility = View.VISIBLE
            } else {
                binding.layoutDurationPicker.visibility = View.GONE
            }
        }

        binding.radioForDuration.isChecked = true

        binding.buttonMinus.setOnClickListener {
            if (customDurationMinutes > 15) {
                customDurationMinutes -= 15
                updateCustomDurationText()
            }
        }

        binding.buttonPlus.setOnClickListener {
            customDurationMinutes += 15
            updateCustomDurationText()
        }

        binding.buttonCancel.setOnClickListener {
            finish()
        }

        binding.buttonConfirm.setOnClickListener {
            confirmPause()
        }
    }

    private fun updateCustomDurationText() {
        val hours = customDurationMinutes / 60
        val minutes = customDurationMinutes % 60
        val timeStr = if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
        binding.textCustomDuration.text = timeStr
        binding.radioForDuration.text = "For $timeStr"
    }

    private fun loadActiveRules() {
        activeRules.clear()
        activeRules.addAll(ruleManager.getEnabledRules())
        
        binding.recyclerRules.layoutManager = LinearLayoutManager(this)
        binding.recyclerRules.adapter = object : RecyclerView.Adapter<RuleViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pause_rule, parent, false)
                return RuleViewHolder(view)
            }

            override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
                val rule = activeRules[position]
                holder.checkbox.text = rule.name
                holder.checkbox.isChecked = selectedRuleIds.contains(rule.id)
                holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedRuleIds.add(rule.id)
                    } else {
                        selectedRuleIds.remove(rule.id)
                    }
                }
            }

            override fun getItemCount() = activeRules.size
        }
    }

    private fun confirmPause() {
        val targetTime = when (binding.radioGroupDuration.checkedRadioButtonId) {
            R.id.radioUntilTurnedOn -> Long.MAX_VALUE
            R.id.radioForDuration -> System.currentTimeMillis() + (customDurationMinutes * 60 * 1000L)
            R.id.radioUntilNextHour -> {
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.timeInMillis
            }
            else -> System.currentTimeMillis() + (60 * 60 * 1000L)
        }

        val prefs = getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val showToast = prefs.getBoolean("toast_notification_action_enabled", true)

        if (mode == "MASTER") {
            prefs.edit().putLong("master_snoozed_until", targetTime).apply()
            
            // Broadcast change to update UI
            val intent = Intent("com.micoyc.speakthat.ACTION_MASTER_SWITCH_CHANGED")
            sendBroadcast(intent)
            
            // Re-run manage notifications to update the persistent notification
            MasterSwitchController.manageNotifications(this, true)

            if (showToast) {
                val timeStr = if (targetTime == Long.MAX_VALUE) "until turned back on" else "until ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(targetTime))}"
                Toast.makeText(this, "SpeakThat paused $timeStr", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (selectedRuleIds.isEmpty()) {
                Toast.makeText(this, "Please select at least one rule", Toast.LENGTH_SHORT).show()
                return
            }

            val updatedCount = selectedRuleIds.size
            val rules = ruleManager.getAllRules().map { rule ->
                if (selectedRuleIds.contains(rule.id)) {
                    rule.copy(snoozedUntil = targetTime, modifiedAt = System.currentTimeMillis())
                } else {
                    rule
                }
            }
            ruleManager.saveRules(rules)

            if (showToast) {
                val timeStr = if (targetTime == Long.MAX_VALUE) "until turned back on" else "until ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(targetTime))}"
                Toast.makeText(this, "$updatedCount rule(s) paused $timeStr", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }

    class RuleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxRule)
    }
}