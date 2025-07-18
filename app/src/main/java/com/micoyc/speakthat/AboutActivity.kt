package com.micoyc.speakthat

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.micoyc.speakthat.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAboutBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about)
        
        // Set up click listeners
        setupClickListeners()
        
        // Load app information
        loadAppInfo()
    }
    
    private fun setupClickListeners() {
        // No custom toolbar click listeners needed with default action bar
    }
    
    private fun loadAppInfo() {
        // App version and build info
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        binding.textVersion.text = getString(R.string.version_format, packageInfo.versionName)
        
        // App description
        binding.textDescription.text = getString(R.string.app_description)
        
        // Developer info
        binding.textDeveloper.text = getString(R.string.developer_info)
        
        // Features list
        binding.textFeatures.text = getString(R.string.app_features)
        
        // License info
        binding.textLicense.text = getString(R.string.license_info)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
} 