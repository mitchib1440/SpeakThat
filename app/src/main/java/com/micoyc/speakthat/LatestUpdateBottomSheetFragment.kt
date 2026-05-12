package com.micoyc.speakthat

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.micoyc.speakthat.utils.ChangelogUtils
import kotlin.math.roundToInt

class LatestUpdateBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_latest_update_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textVersionHeader: TextView = view.findViewById(R.id.textVersionHeader)
        val layoutChangelogContent: LinearLayout = view.findViewById(R.id.layoutChangelogContent)

        // Set Header
        textVersionHeader.text = "SpeakThat! v${BuildConfig.VERSION_NAME}"

        // Load changelog items
        val changelogItems = ChangelogUtils.getChangelogItems(requireContext())

        // Populate items
        val context = requireContext()
        val spacingSmall = (4 * resources.displayMetrics.density).roundToInt()
        val spacingMedium = (12 * resources.displayMetrics.density).roundToInt()
        val spacingLarge = (16 * resources.displayMetrics.density).roundToInt()
        val indentSize = (16 * resources.displayMetrics.density).roundToInt()

        for (item in changelogItems) {
            // Main Text
            val itemTextView = TextView(context).apply {
                text = item.text
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.purple_card_text_primary))
                setPadding(0, spacingMedium, 0, spacingSmall)
            }
            layoutChangelogContent.addView(itemTextView)

            // Subpoints
            val safeSubpoints = item.subpoints ?: emptyList()
            if (safeSubpoints.isNotEmpty()) {
                for (subpoint in safeSubpoints) {
                    val subpointView = TextView(context).apply {
                        text = "• $subpoint"
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(context, R.color.purple_card_text_secondary))
                        setPadding(indentSize, spacingSmall, 0, spacingSmall)
                        setLineSpacing(0f, 1.1f)
                    }
                    layoutChangelogContent.addView(subpointView)
                }
            }
        }
    }
}
