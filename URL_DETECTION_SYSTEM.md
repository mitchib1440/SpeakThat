# URL Detection and Handling System

## Overview

The URL Detection and Handling system in SpeakThat provides users with granular control over how web links are read aloud in notifications. This feature addresses the common issue of excessively long and often irrelevant URLs (such as Amazon product links with tracking parameters) being read verbatim.

## Features

### Three URL Handling Modes

1. **Read Full Link** (`read_full`)
   - Reads the entire URL as-is
   - Default behavior for users who want complete information
   - No processing applied

2. **Read Domain Only** (`domain_only`) - **Default Mode**
   - Extracts and reads only the domain name
   - Removes protocol (http/https), www prefix, paths, and query parameters
   - Examples:
     - `https://www.amazon.com/dp/B08N5WRWNW?ref=sr_1_1` → `amazon.com`
     - `www.speakthat.app/subdirectory` → `speakthat.app`
     - `localhost:8080/api` → `localhost`

3. **Don't Read** (`dont_read`)
   - Completely omits URLs from speech
   - Supports custom replacement text
   - If custom text is empty, URLs are omitted entirely (like Word Swap feature)

## Technical Implementation

### URL Detection Regex Pattern

```kotlin
// Pre-compiled regex for better performance (includes IPv6 support)
private val URL_PATTERN = Regex("""(?i)(?:https?://[^\s]+|www\.[^\s]+|(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.(?:[a-zA-Z]{2,}|[0-9]+)|\[[0-9a-fA-F:]+\])(?::[0-9]+)?(?:/[^\s]*)?)""")
```

This pattern matches:
- **Full URLs**: `https://example.com/path` or `http://example.com`
- **www URLs**: `www.example.com/path`
- **Domain with TLD**: `example.com` (must have valid top-level domain)
- **IPv4 addresses**: `192.168.1.1:8080`
- **IPv6 addresses**: `[2001:db8::1]:8080`

### Domain Extraction Algorithm

```kotlin
private fun extractDomain(url: String): String {
    try {
        // Validate input
        if (url.isBlank()) {
            Log.w(TAG, "Empty URL provided to extractDomain")
            return "link"
        }
        
        // Remove protocol if present (https:// or http://)
        var cleanUrl = url.replace(Regex("^https?://"), "")
        
        // Remove www. prefix if present
        cleanUrl = cleanUrl.replace(Regex("^www\\."), "")
        
        // Split by / to get the host part
        val hostPart = cleanUrl.split("/")[0]
        
        // Split by : to remove port if present
        val domainPart = hostPart.split(":")[0]
        
        // Validate domain part
        if (domainPart.isBlank()) {
            Log.w(TAG, "Empty domain part extracted from URL '$url'")
            return "link"
        }
        
        // For localhost and IP addresses, return as-is
        if (domainPart == "localhost" || domainPart.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) {
            return domainPart
        }
        
        // For IPv6 addresses (in brackets), return as-is
        if (domainPart.startsWith("[") && domainPart.endsWith("]")) {
            return domainPart
        }
        
        // For regular domains, extract the main domain (last two parts)
        val parts = domainPart.split(".")
        return if (parts.size >= 2) {
            parts.takeLast(2).joinToString(".")
        } else {
            domainPart
        }
    } catch (e: Exception) {
        Log.w(TAG, "Error extracting domain from URL '$url': ${e.message}")
        // Return a safe fallback instead of original URL to avoid reading long URLs
        return "link"
    }
}
```

### Processing Pipeline

1. **Word Filtering**: Applied first (existing Word Swap functionality)
2. **URL Handling**: Applied to non-private notifications only
3. **Integration Point**: `applyUrlHandling()` method within `applyWordFiltering()`

### Settings Management

- **Storage**: SharedPreferences with keys `url_handling_mode` and `url_replacement_text`
- **Default Values**: `domain_only` mode, empty replacement text
- **Import/Export**: Fully compatible with app's configuration system
- **Search Index**: Discoverable via keywords: "url", "link", "web", "domain", "website", "http", "https", "www", "shorten", "omit"

## User Interface

### Filter Settings Integration

- **Location**: Filter Settings → URL Handling section (below Word Swap)
- **UI Components**:
  - Radio buttons for mode selection
  - Conditional text input for custom replacement
  - Example URL display: `https://www.speakthat.app/subdirectory → speakthat.app`
  - Material Design icons and consistent theming

### Localization

All user-facing text is externalized to `strings.xml` for translation via Weblate:
- Section titles and descriptions
- Radio button labels
- Help text and examples
- Custom text input hints

## Examples

### Domain-Only Mode Examples

| Original URL | Spoken Result |
|--------------|---------------|
| `https://www.amazon.com/dp/B08N5WRWNW?ref=sr_1_1` | `amazon.com` |
| `www.speakthat.app/subdirectory` | `speakthat.app` |
| `http://localhost:8080/api/users` | `localhost` |
| `192.168.1.1:3000/dashboard` | `192.168.1.1` |
| `subdomain.example.co.uk/path` | `example.co.uk` |

### Don't Read Mode Examples

| Custom Text | Original URL | Spoken Result |
|-------------|--------------|---------------|
| (empty) | `https://example.com` | (omitted entirely) |
| `web link` | `https://example.com` | `web link` |
| `link to site` | `https://example.com` | `link to site` |

## Integration Points

### Core Service Integration
- **File**: `NotificationReaderService.kt`
- **Method**: `applyUrlHandling()` within `applyWordFiltering()`
- **Trigger**: Only for non-private notifications

### UI Integration
- **File**: `FilterSettingsActivity.java`
- **Components**: Radio buttons, text input, conditional visibility
- **Persistence**: Real-time saving to SharedPreferences

### Configuration Management
- **File**: `FilterConfigManager.java`
- **Features**: Import/export compatibility, default fallbacks
- **Backward Compatibility**: Graceful handling of missing settings

### Search Integration
- **Files**: `SettingsDatabase.kt` (both store and GitHub variants)
- **Keywords**: Comprehensive search terms for discoverability

## Logging and Debugging

### Debug Information
- **URL Processing**: Logs original URL and replacement result
- **Settings Loading**: Logs current mode and replacement text
- **Mode**: `F/Filter` category for easy filtering

## Performance Considerations

- **Regex Efficiency**: Pre-compiled regex pattern for optimal performance
- **Input Validation**: Skips processing for texts longer than 10,000 characters
- **Processing Order**: URL handling after word filtering to avoid conflicts
- **Memory Usage**: Minimal overhead with string replacement operations
- **Battery Impact**: Negligible - only processes text during notification reading

## Security Considerations

- **Input Validation**: Regex-based validation prevents injection attacks
- **Error Handling**: Enhanced error handling with safe fallbacks ("link" instead of original URL)
- **Input Limits**: Prevents processing of extremely long texts to avoid performance issues
- **Privacy**: No URL data stored or transmitted externally

## Recent Improvements

### Version 1.5.0+ Enhancements

1. **Performance Optimizations**:
   - Pre-compiled regex pattern for better performance
   - Input validation to skip processing of very long texts (>10,000 characters)

2. **Enhanced Error Handling**:
   - Input validation for empty URLs and domain parts
   - Safe fallback to "link" instead of original URL on errors
   - Comprehensive logging for debugging

3. **IPv6 Support**:
   - Added support for IPv6 addresses in brackets (e.g., `[2001:db8::1]:8080`)
   - Proper handling of IPv6 addresses in domain extraction

4. **Robustness Improvements**:
   - Better handling of edge cases and malformed URLs
   - Enhanced logging for troubleshooting
   - Graceful degradation on errors
