package com.micoyc.speakthat

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * This activity only exists in the GitHub flavor
 * It demonstrates how flavor-specific code works
 */
class GitHubSpecificActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This code only exists in the GitHub variant
        Toast.makeText(this, "GitHub variant - Auto-updater enabled", Toast.LENGTH_SHORT).show()
        
        // You could launch update functionality here
        UpdateFeature.startUpdateActivity(this, forceCheck = true)
        
        finish()
    }
} 