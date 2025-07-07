package com.micoyc.speakthat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.micoyc.speakthat.databinding.ItemOnboardingPageBinding

class OnboardingPagerAdapter(private val skipPermissionPage: Boolean = false) : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {
    
    private val pages = if (skipPermissionPage) listOf(
        OnboardingPage(
            title = "Welcome to SpeakThat!",
            description = "Stay connected without staring at your screen. Your phone will read notifications aloud so you can focus on what matters.",
            icon = "🔊"
        ),
        OnboardingPage(
            title = "Your Privacy Matters",
            description = "🔒 We'll never read sensitive apps (banking, medical)\n\n📱 Shake your phone to stop any announcement instantly\n\n⚙️ You control exactly what gets read aloud\n\n🔐 Everything stays on your device",
            icon = "🔒"
        ),
        OnboardingPage(
            title = "You're All Set!",
            description = "SpeakThat is ready to help you stay connected while keeping your eyes free. Try it now!",
            icon = "✅"
        )
    ) else listOf(
        OnboardingPage(
            title = "Welcome to SpeakThat!",
            description = "Stay connected without staring at your screen. Your phone will read notifications aloud so you can focus on what matters.",
            icon = "🔊"
        ),
        OnboardingPage(
            title = "Notification Access Required",
            description = "SpeakThat needs permission to read your notifications aloud.\n\n🔒 Everything stays on your device\n📱 No data is sent to us or anyone else\n⚙️ You control what gets read\n💬 We only read what you allow\n💡 Tip: In the list, tap 'SpeakThat' to enable",
            icon = "🔔",
            showPermissionButton = true
        ),
        OnboardingPage(
            title = "Your Privacy Matters",
            description = "🔒 We'll never read sensitive apps (banking, medical)\n\n📱 Shake your phone to stop any announcement instantly\n\n⚙️ You control exactly what gets read aloud\n\n🔐 Everything stays on your device",
            icon = "🔒"
        ),
        OnboardingPage(
            title = "You're All Set!",
            description = "SpeakThat is ready to help you stay connected while keeping your eyes free. Try it now!",
            icon = "✅"
        )
    )
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return OnboardingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }
    
    override fun getItemCount(): Int = pages.size
    
    class OnboardingViewHolder(private val binding: ItemOnboardingPageBinding) : 
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(page: OnboardingPage) {
            android.util.Log.d("OnboardingPagerAdapter", "Binding page: ${page.title}")
            binding.textTitle.text = page.title
            binding.textDescription.text = page.description
            binding.textIcon.text = page.icon
            
            // Show/hide permission button based on page
            if (page.showPermissionButton) {
                binding.buttonPermission.visibility = android.view.View.VISIBLE
                binding.buttonPermission.text = "Open Notification Settings"
                binding.buttonPermission.setOnClickListener {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    binding.root.context.startActivity(intent)
                }
            } else {
                binding.buttonPermission.visibility = android.view.View.GONE
            }
        }
    }
    
    data class OnboardingPage(
        val title: String,
        val description: String,
        val icon: String,
        val showPermissionButton: Boolean = false
    )
} 