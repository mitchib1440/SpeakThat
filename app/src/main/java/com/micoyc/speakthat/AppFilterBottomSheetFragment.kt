/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch

class AppFilterBottomSheetFragment : BottomSheetDialogFragment() {

    interface Listener {
        fun onAppFilterChanged(
            showSystemApps: Boolean,
            showSelectedOnly: Boolean,
            sortOrder: SortOrder
        )
    }

    enum class SortOrder {
        AZ, ZA
    }

    private var listener: Listener? = null
    private var suppressCallbacks = false

    private lateinit var switchShowSystemApps: MaterialSwitch
    private lateinit var switchShowSelectedOnly: MaterialSwitch
    private lateinit var radioSortGroup: RadioGroup

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is Listener -> parentFragment as Listener
            context is Listener -> context
            else -> null
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_app_filter_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchShowSystemApps = view.findViewById(R.id.switchShowSystemApps)
        switchShowSelectedOnly = view.findViewById(R.id.switchShowSelectedOnly)
        radioSortGroup = view.findViewById(R.id.radioSortGroup)

        val showSystemApps = arguments?.getBoolean(ARG_SHOW_SYSTEM_APPS, false) ?: false
        val showSelectedOnly = arguments?.getBoolean(ARG_SHOW_SELECTED_ONLY, false) ?: false
        val sortOrder = arguments?.getString(ARG_SORT_ORDER)?.let {
            runCatching { SortOrder.valueOf(it) }.getOrElse { SortOrder.AZ }
        } ?: SortOrder.AZ

        suppressCallbacks = true
        switchShowSystemApps.isChecked = showSystemApps
        switchShowSelectedOnly.isChecked = showSelectedOnly
        radioSortGroup.check(if (sortOrder == SortOrder.ZA) R.id.radioSortZa else R.id.radioSortAz)
        suppressCallbacks = false

        switchShowSystemApps.setOnCheckedChangeListener { _, _ -> notifyHost() }
        switchShowSelectedOnly.setOnCheckedChangeListener { _, _ -> notifyHost() }
        radioSortGroup.setOnCheckedChangeListener { _, _ -> notifyHost() }
    }

    private fun notifyHost() {
        if (suppressCallbacks) return

        listener?.onAppFilterChanged(
            showSystemApps = switchShowSystemApps.isChecked,
            showSelectedOnly = switchShowSelectedOnly.isChecked,
            sortOrder = getCurrentSortOrder()
        )
    }

    private fun getCurrentSortOrder(): SortOrder {
        return if (radioSortGroup.checkedRadioButtonId == R.id.radioSortZa) {
            SortOrder.ZA
        } else {
            SortOrder.AZ
        }
    }

    companion object {
        private const val ARG_SHOW_SYSTEM_APPS = "arg_show_system_apps"
        private const val ARG_SHOW_SELECTED_ONLY = "arg_show_selected_only"
        private const val ARG_SORT_ORDER = "arg_sort_order"

        fun newInstance(
            showSystemApps: Boolean,
            showSelectedOnly: Boolean,
            sortOrder: SortOrder
        ): AppFilterBottomSheetFragment {
            return AppFilterBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SHOW_SYSTEM_APPS, showSystemApps)
                    putBoolean(ARG_SHOW_SELECTED_ONLY, showSelectedOnly)
                    putString(ARG_SORT_ORDER, sortOrder.name)
                }
            }
        }
    }
}
