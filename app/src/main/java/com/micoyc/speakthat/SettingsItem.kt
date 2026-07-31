/*
 * SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.
 * This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.
 * SpeakThat! Copyright © Mitchell Bell
 * SPEAKTHAT is a registered UK trademark of Mitchell Bell
 */

package com.micoyc.speakthat

data class SettingsItem(
    val id: String,
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val descriptionRes: Int,
    val category: String,
    @androidx.annotation.StringRes val categoryTitleRes: Int,
    @androidx.annotation.DrawableRes val categoryIconRes: Int,
    val settingType: SettingType,
    @androidx.annotation.StringRes val searchKeywordsRes: Int? = null,
    val supportedFlavors: List<String> = listOf("github", "store", "play"),
    val navigationAction: (android.content.Context, String) -> Unit
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