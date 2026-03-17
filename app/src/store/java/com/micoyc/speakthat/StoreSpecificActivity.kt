/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * This activity only exists in the Store flavor
 * It demonstrates how flavor-specific code works
 */
class StoreSpecificActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // This code only exists in the Store variant
        Toast.makeText(this, "Store variant - Updates via app store", Toast.LENGTH_SHORT).show()
        
        // Store variant doesn't have update functionality
        // UpdateFeature.startUpdateActivity(this, forceCheck = true) // This would not compile in store variant
        
        finish()
    }
} 