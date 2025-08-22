package com.micoyc.speakthat

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.micoyc.speakthat.rules.RuleTemplate

class TimeScheduleDialogFragment : DialogFragment() {
    
    private var template: RuleTemplate? = null
    private var onRuleCreated: ((RuleTemplate, Map<String, Any>) -> Unit)? = null
    
    companion object {
        fun newInstance(template: RuleTemplate, onRuleCreated: (RuleTemplate, Map<String, Any>) -> Unit): TimeScheduleDialogFragment {
            return TimeScheduleDialogFragment().apply {
                this.template = template
                this.onRuleCreated = onRuleCreated
            }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val template = this.template ?: throw IllegalStateException("Template not set")
        
        // Determine which layout to use based on screen size
        val displayMetrics = requireContext().resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels
        
        // Use compact layout for very small screens
        val layoutResId = when {
            screenHeight < 600 || screenWidth < 400 -> R.layout.dialog_time_schedule_compact
            else -> R.layout.dialog_time_schedule_configuration
        }
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(layoutResId, null)
        
        // Set up time pickers
        val timePickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerStart)
        val timePickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.timePickerEnd)
        val checkBoxEveryDay = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxEveryDay)
        
        // Set default times (10 PM to 8 AM)
        timePickerStart.hour = 22
        timePickerStart.minute = 0
        timePickerEnd.hour = 8
        timePickerEnd.minute = 0
        
        // Set up "Every Day" checkbox functionality if it exists
        checkBoxEveryDay?.setOnCheckedChangeListener { _, isChecked ->
            val checkBoxMonday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxMonday)
            val checkBoxTuesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxTuesday)
            val checkBoxWednesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxWednesday)
            val checkBoxThursday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxThursday)
            val checkBoxFriday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxFriday)
            val checkBoxSaturday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSaturday)
            val checkBoxSunday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSunday)
            
            checkBoxMonday.isChecked = isChecked
            checkBoxTuesday.isChecked = isChecked
            checkBoxWednesday.isChecked = isChecked
            checkBoxThursday.isChecked = isChecked
            checkBoxFriday.isChecked = isChecked
            checkBoxSaturday.isChecked = isChecked
            checkBoxSunday.isChecked = isChecked
        }
        
        return AlertDialog.Builder(requireContext())
            .setTitle("Configure ${template.name}")
            .setView(dialogView)
            .setPositiveButton("Create Rule") { _, _ ->
                // Collect selected days
                val selectedDays = mutableSetOf<Int>()
                val checkBoxMonday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxMonday)
                val checkBoxTuesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxTuesday)
                val checkBoxWednesday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxWednesday)
                val checkBoxThursday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxThursday)
                val checkBoxFriday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxFriday)
                val checkBoxSaturday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSaturday)
                val checkBoxSunday = dialogView.findViewById<android.widget.CheckBox>(R.id.checkBoxSunday)
                
                if (checkBoxMonday.isChecked) selectedDays.add(1)
                if (checkBoxTuesday.isChecked) selectedDays.add(2)
                if (checkBoxWednesday.isChecked) selectedDays.add(3)
                if (checkBoxThursday.isChecked) selectedDays.add(4)
                if (checkBoxFriday.isChecked) selectedDays.add(5)
                if (checkBoxSaturday.isChecked) selectedDays.add(6)
                if (checkBoxSunday.isChecked) selectedDays.add(7)
                
                // Create custom data for the rule
                val customData = mapOf(
                    "startHour" to timePickerStart.hour,
                    "startMinute" to timePickerStart.minute,
                    "endHour" to timePickerEnd.hour,
                    "endMinute" to timePickerEnd.minute,
                    "selectedDays" to selectedDays
                )
                
                onRuleCreated?.invoke(template, customData)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}
