package com.micoyc.speakthat

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.micoyc.speakthat.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: OnboardingPagerAdapter
    private var skipPermissionPage = false
    
    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        
        fun hasSeenOnboarding(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // Initialize view binding
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Determine if we should skip the permission page
        skipPermissionPage = isNotificationServiceEnabled()
        setupOnboarding()
    }
    
    override fun onResume() {
        super.onResume()
        // Force a full rebind to ensure permission button is visible and text updates
        adapter.notifyDataSetChanged()
        val currentPage = binding.viewPager.currentItem
        updateButtonText(currentPage, adapter.itemCount)
    }
    
    private fun setupOnboarding() {
        // Set up ViewPager2 with or without permission page
        adapter = if (skipPermissionPage) {
            OnboardingPagerAdapter(skipPermissionPage = true)
        } else {
            OnboardingPagerAdapter()
        }
        binding.viewPager.adapter = adapter
        
        // Set up page indicator dots
        setupPageIndicator(adapter.itemCount)
        
        // Set up button listeners
        binding.buttonSkip.setOnClickListener {
            completeOnboarding()
        }
        
        binding.buttonNext.setOnClickListener {
            if (binding.viewPager.currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
        
        // Prevent swiping past permission page until permission is granted
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtonText(position, adapter.itemCount)
                updatePageIndicator(position)
            }
        })
        binding.viewPager.isUserInputEnabled = true // default
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                val currentPage = binding.viewPager.currentItem
                if (!skipPermissionPage && currentPage == 1 && !isNotificationServiceEnabled()) {
                    // Disable swiping on permission page if permission not granted
                    binding.viewPager.isUserInputEnabled = false
                } else {
                    binding.viewPager.isUserInputEnabled = true
                }
            }
        })
        
        // Set initial button text
        updateButtonText(0, adapter.itemCount)
    }
    
    private fun setupPageIndicator(pageCount: Int) {
        binding.pageIndicator.removeAllViews()
        for (i in 0 until pageCount) {
            val dot = android.widget.ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                    setMargins(0, 0, 8, 0) // left, top, right, bottom
                }
                setImageResource(R.drawable.tab_selector)
                isClickable = false
                isFocusable = false
            }
            binding.pageIndicator.addView(dot)
        }
        updatePageIndicator(0)
    }
    
    private fun updatePageIndicator(currentPage: Int) {
        for (i in 0 until binding.pageIndicator.childCount) {
            val dot = binding.pageIndicator.getChildAt(i) as android.widget.ImageView
            dot.isSelected = (i == currentPage)
        }
    }
    
    private fun updateButtonText(currentPage: Int, totalPages: Int) {
        if (currentPage == totalPages - 1) {
            binding.buttonNext.text = "Get Started"
        } else {
            binding.buttonNext.text = "Next"
        }
        
        // Disable Next button on permission page if permissions not granted
        if (!skipPermissionPage && currentPage == 1) { // Permission page
            val hasPermission = isNotificationServiceEnabled()
            binding.buttonNext.isEnabled = hasPermission
            if (!hasPermission) {
                binding.buttonNext.text = "Grant Permission First"
            }
        } else {
            binding.buttonNext.isEnabled = true
        }
    }
    
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = packageName
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!android.text.TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val componentName = android.content.ComponentName.unflattenFromString(name)
                val nameMatch = android.text.TextUtils.equals(packageName, componentName?.packageName)
                if (nameMatch) {
                    return true
                }
            }
        }
        return false
    }
    
    private fun completeOnboarding() {
        // Mark onboarding as completed
        sharedPreferences.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, true).apply()
        
        // Start main activity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openNotificationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
    
    fun refreshPermissionStatus() {
        // Update the current page to reflect permission status
        val currentPage = binding.viewPager.currentItem
        updateButtonText(currentPage, binding.viewPager.adapter?.itemCount ?: 4)
    }
} 