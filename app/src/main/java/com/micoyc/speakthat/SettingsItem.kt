/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

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