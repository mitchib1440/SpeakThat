# SpeakThat Localization Improvements

## Overview

This document describes the improved localization system implemented in the `localization-ii` branch. The new system addresses the issues identified in the forum discussion about proper sentence structure and word order in different languages.

## Problem with Previous Approach

The previous localization system used individual phrase replacements:

```kotlin
// OLD APPROACH (Problematic)
processedTemplate = processedTemplate
    .replace("notified you:", getLocalizedTtsString(R.string.tts_notified_you))
    .replace("reported:", getLocalizedTtsString(R.string.tts_reported))
    .replace("saying", getLocalizedTtsString(R.string.tts_saying))
    // ... etc
```

**Issues:**
1. **Word order problems**: Different languages have different sentence structures
2. **Context dependency**: Phrases like "saying" vs "saying:" need different translations
3. **Inflexibility**: Can't handle complex grammatical variations
4. **Maintenance burden**: Each new template requires new individual phrases

## New Template-Based Approach

The new system uses complete sentence templates with placeholders:

### 1. Template String Resources

**English (default):**
```xml
<string name="tts_template_notified_you">{app} notified you: {content}</string>
<string name="tts_template_reported">{app} reported: {content}</string>
<string name="tts_template_saying">Notification from {app}, saying {content}</string>
```

**Italian:**
```xml
<string name="tts_template_notified_you">{app} ti ha notificato: {content}</string>
<string name="tts_template_reported">{app} ha riportato: {content}</string>
<string name="tts_template_saying">Notifica da {app}, che dice {content}</string>
```

**German:**
```xml
<string name="tts_template_notified_you">{app} hat dich benachrichtigt: {content}</string>
<string name="tts_template_reported">{app} hat gemeldet: {content}</string>
<string name="tts_template_saying">Benachrichtigung von {app}, die sagt {content}</string>
```

### 2. New Methods

#### `getLocalizedTemplate(templateKey, appName, content)`
```kotlin
private fun getLocalizedTemplate(templateKey: String, appName: String, content: String): String {
    val templateResId = getTemplateResourceId(templateKey)
    val template = getLocalizedTtsString(templateResId)
    
    // Replace placeholders with actual values
    val result = template
        .replace("{app}", appName)
        .replace("{content}", content)
    
    return result
}
```

#### `getTemplateResourceId(templateKey)`
```kotlin
private fun getTemplateResourceId(templateKey: String): Int {
    return when (templateKey) {
        "notified_you" -> R.string.tts_template_notified_you
        "reported" -> R.string.tts_template_reported
        "saying" -> R.string.tts_template_saying
        // ... etc
    }
}
```

#### `getLocalizedVariedFormatsImproved()`
```kotlin
private fun getLocalizedVariedFormatsImproved(): Array<String> {
    return arrayOf(
        getLocalizedTemplate("notified_you", "{app}", "{content}"),
        getLocalizedTemplate("reported", "{app}", "{content}"),
        getLocalizedTemplate("saying", "{app}", "{content}"),
        // ... etc
    )
}
```

### 3. Implementation Details

#### Feature Flag System
The new system includes a feature flag for gradual rollout:

```kotlin
val useNewTemplateSystem = true // TODO: Make this configurable via settings
if (useNewTemplateSystem) {
    getLocalizedVariedFormatsImproved().random()
} else {
    getLocalizedVariedFormats().random()
}
```

#### Backward Compatibility
- Old methods are preserved for backward compatibility
- Old phrase-by-phrase localization is only applied when new system is disabled
- Private notification handling updated to support new templates

#### Private Notifications
```kotlin
// New template-based approach
processedText = if (useNewTemplateSystem) {
    getLocalizedTemplate("private_notification", appName, "")
} else {
    "${getLocalizedTtsString(R.string.tts_private_notification_received)} $appName"
}
```

## Benefits of New System

### 1. **Proper Grammar**
Each language can have correct word order and sentence structure:
- **English**: "Signal notified you: Hello world"
- **Italian**: "Signal ti ha notificato: Hello world"
- **German**: "Signal hat dich benachrichtigt: Hello world"

### 2. **Context Awareness**
No more confusion between different contexts:
- **English**: "saying" vs "saying:"
- **Italian**: "dicendo" vs "che dice"
- **German**: "sagt" vs "die sagt"

### 3. **Maintainability**
- Easy to add new languages
- Consistent template structure
- Clear separation of concerns

### 4. **Flexibility**
- Can handle complex grammatical structures
- Supports different sentence patterns per language
- Easy to extend with new templates

## Testing

The implementation includes a test method `testTemplateLocalization()` that demonstrates the differences:

```kotlin
private fun testTemplateLocalization() {
    val testAppName = "TestApp"
    val testContent = "Hello world"
    
    // Test old system
    Log.d(TAG, "Old system - notified_you: {app} ${getLocalizedTtsString(R.string.tts_notified_you)} {content}")
    
    // Test new system
    val newResult = getLocalizedTemplate("notified_you", testAppName, testContent)
    Log.d(TAG, "New system - notified_you: $newResult")
    
    // Test varied formats
    val oldVaried = getLocalizedVariedFormats()
    val newVaried = getLocalizedVariedFormatsImproved()
    
    Log.d(TAG, "Old varied format example: ${oldVaried[0]}")
    Log.d(TAG, "New varied format example: ${newVaried[0]}")
}
```

## Migration Strategy

1. **Phase 1**: ✅ Implement new template system alongside old system
2. **Phase 2**: ✅ Add feature flag for gradual rollout
3. **Phase 3**: Test with different languages and TTS settings
4. **Phase 4**: Make feature flag configurable via settings
5. **Phase 5**: Remove old system once fully tested

## Current Status

- ✅ New template strings added for English, Italian, and German
- ✅ New methods implemented with backward compatibility
- ✅ Feature flag system in place
- ✅ Private notification handling updated
- ✅ Test method implemented
- ✅ Build successful

## Next Steps

1. **Test the app** with different TTS language settings
2. **Add more languages** (Spanish, French, etc.)
3. **Make feature flag configurable** via Voice Settings
4. **Remove old system** once fully tested
5. **Update documentation** for translators

## Files Modified

- `app/src/main/res/values/strings.xml` - Added new template strings
- `app/src/main/res/values-it/strings.xml` - Added Italian template translations
- `app/src/main/res/values-de/strings.xml` - Added German template translations
- `app/src/main/java/com/micoyc/speakthat/NotificationReaderService.kt` - Implemented new methods

## Conclusion

This new template-based localization system solves the fundamental issues with the previous approach by:

1. **Respecting language-specific grammar** and word order
2. **Providing context-aware translations** for different sentence structures
3. **Maintaining backward compatibility** during the transition
4. **Enabling easy expansion** to new languages and templates

The system is now ready for testing and can be gradually rolled out to users. 