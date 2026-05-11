package com.micoyc.speakthat

import android.content.Context
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.utils.SeasonalModeHelper

class AppearanceActivity : AppCompatActivity() {

    private lateinit var adapter: BadgeGridAdapter
    private lateinit var textTotalReads: TextView
    private lateinit var progressNextBadge: ProgressBar
    private lateinit var textNextBadgeThreshold: TextView
    private lateinit var recyclerViewBadges: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved theme
        val sharedPreferences = getSharedPreferences("SpeakThatPrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean("dark_mode", true)
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        setContentView(R.layout.activity_appearance)

        supportActionBar?.apply {
            title = getString(R.string.style_title)
            setDisplayHomeAsUpEnabled(true)
        }

        textTotalReads = findViewById(R.id.textTotalReads)
        progressNextBadge = findViewById(R.id.progressNextBadge)
        textNextBadgeThreshold = findViewById(R.id.textNextBadgeThreshold)
        recyclerViewBadges = findViewById(R.id.recyclerViewBadges)

        val totalReads = StatisticsManager.getInstance(this).getNotificationsRead()
        textTotalReads.text = "$totalReads Notifications Read"

        val unlockedTiers = BadgeAssets.getUnlockedBadges(this)

        // Find next locked badge
        val allTiers = BadgeAssets.BadgeTier.values().toList()
        val nextLockedTier = allTiers.reversed().firstOrNull { !unlockedTiers.contains(it) }

        if (nextLockedTier != null) {
            progressNextBadge.max = nextLockedTier.requiredReads
            progressNextBadge.progress = totalReads
            textNextBadgeThreshold.text = "Next badge at ${nextLockedTier.requiredReads} reads"
        } else {
            progressNextBadge.max = 1
            progressNextBadge.progress = 1
            textNextBadgeThreshold.text = "All badges unlocked!"
        }

        val currentSelection = sharedPreferences.getString(BadgeAssets.PREF_BADGE_SELECTION, BadgeAssets.KEY_DEFAULT) ?: BadgeAssets.KEY_DEFAULT
        val validSelection = BadgeAssets.ensureValidSelection(currentSelection, this)
        
        // Save back if it was invalid
        if (currentSelection != validSelection) {
            sharedPreferences.edit().putString(BadgeAssets.PREF_BADGE_SELECTION, validSelection).apply()
        }

        val festiveEnabled = SeasonalModeHelper.isFestiveEnabled(this)

        adapter = BadgeGridAdapter(
            allTiers = allTiers,
            unlockedTiers = unlockedTiers,
            selectedKey = validSelection,
            festiveEnabled = festiveEnabled
        ) { tier, isUnlocked ->
            if (isUnlocked) {
                sharedPreferences.edit().putString(BadgeAssets.PREF_BADGE_SELECTION, tier.key).apply()
                adapter.selectedKey = tier.key
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Badge equipped!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unlock by reading ${tier.requiredReads} notifications!", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerViewBadges.adapter = adapter
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
