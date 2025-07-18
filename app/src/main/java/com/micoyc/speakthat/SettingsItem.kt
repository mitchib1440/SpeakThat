package com.micoyc.speakthat

data class SettingsItem(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val categoryTitle: String,
    val categoryIcon: String,
    val settingType: SettingType,
    val searchKeywords: List<String> = emptyList(),
    val navigationAction: () -> Unit
)

enum class SettingType {
    SWITCH,
    SEEK_BAR,
    SPINNER,
    BUTTON,
    TEXT_INPUT,
    RADIO_GROUP,
    CARD
} 