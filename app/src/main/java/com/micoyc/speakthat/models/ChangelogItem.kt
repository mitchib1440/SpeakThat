package com.micoyc.speakthat.models

data class ChangelogItem(
    val text: String,
    val subpoints: List<String>? = emptyList()
)
