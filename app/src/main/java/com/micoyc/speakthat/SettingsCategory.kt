package com.micoyc.speakthat

import android.view.View

data class SettingsCategory(
    val id: String,
    val title: String,
    val description: String,
    val cardView: View,
    val onClickAction: () -> Unit
) 