package com.micoyc.speakthat;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class NotificationFilterHelper {
    
    public static class FilterSuggestion {
        public final String originalText;
        public final String appName;
        public final String packageName;
        public final String exactMatch;
        public final String patternMatch;
        public final String keywordMatch;
        public final String description;
        
        public FilterSuggestion(String originalText, String appName, String packageName) {
            this.originalText = originalText;
            this.appName = appName;
            this.packageName = packageName;
            this.exactMatch = originalText;
            this.patternMatch = generatePatternMatch(originalText);
            this.keywordMatch = generateKeywordMatch(originalText);
            this.description = generateDescription();
        }
        
        private String generatePatternMatch(String text) {
            String pattern = text;
            
            // Remove common time patterns
            pattern = pattern.replaceAll("\\b\\d{1,2}:\\d{2}\\s*(AM|PM|am|pm)?\\b", "[TIME]");
            pattern = pattern.replaceAll("\\b\\d{1,2}:\\d{2}:\\d{2}\\b", "[TIME]");
            
            // Remove date patterns
            pattern = pattern.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", "[DATE]");
            pattern = pattern.replaceAll("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s*\\d{0,4}\\b", "[DATE]");
            pattern = pattern.replaceAll("\\b\\d{1,2}\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s*\\d{0,4}\\b", "[DATE]");
            
            // Remove standalone numbers (but keep numbers that are part of words)
            pattern = pattern.replaceAll("\\b\\d+\\b", "[NUMBER]");
            
            // Remove percentages
            pattern = pattern.replaceAll("\\b\\d+%", "[PERCENT]");
            
            // Remove file sizes (MB, GB, KB)
            pattern = pattern.replaceAll("\\b\\d+(\\.\\d+)?\\s*(MB|GB|KB|TB)\\b", "[SIZE]");
            
            // Remove URLs
            pattern = pattern.replaceAll("https?://[^\\s]+", "[URL]");
            
            // Remove email addresses
            pattern = pattern.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "[EMAIL]");
            
            // Clean up multiple spaces
            pattern = pattern.replaceAll("\\s+", " ").trim();
            
            return pattern;
        }
        
        private String generateKeywordMatch(String text) {
            String keywords = text;
            
            // Remove all numbers, dates, times, etc.
            keywords = keywords.replaceAll("\\b\\d{1,2}:\\d{2}(:\\d{2})?\\s*(AM|PM|am|pm)?\\b", "");
            keywords = keywords.replaceAll("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", "");
            keywords = keywords.replaceAll("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s*\\d{0,4}\\b", "");
            keywords = keywords.replaceAll("\\b\\d+(\\.\\d+)?\\s*(MB|GB|KB|TB|%)?\\b", "");
            keywords = keywords.replaceAll("https?://[^\\s]+", "");
            keywords = keywords.replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "");
            
            // Remove common connecting words and keep only meaningful words
            keywords = keywords.replaceAll("\\b(the|and|or|but|in|on|at|to|for|of|with|by|from|up|about|into|through|during|before|after|above|below|between|among|around|since|until|while|because|although|if|when|where|how|what|who|which|that|this|these|those|a|an|is|are|was|were|been|be|have|has|had|do|does|did|will|would|could|should|may|might|can|must|shall)\\b", "");
            
            // Clean up and get core words
            keywords = keywords.replaceAll("[^a-zA-Z\\s]", "").replaceAll("\\s+", " ").trim();
            
            // Take first few meaningful words
            String[] words = keywords.split("\\s+");
            StringBuilder coreKeywords = new StringBuilder();
            int wordCount = 0;
            for (String word : words) {
                if (word.length() > 2 && wordCount < 4) { // Only words longer than 2 chars, max 4 words
                    if (coreKeywords.length() > 0) coreKeywords.append(" ");
                    coreKeywords.append(word.toLowerCase());
                    wordCount++;
                }
            }
            
            return coreKeywords.toString();
        }
        
        private String generateDescription() {
            if (!patternMatch.equals(originalText)) {
                return "Pattern-based filter (removes dates, times, numbers)";
            } else if (!keywordMatch.isEmpty()) {
                return "Keyword-based filter (core words only)";
            } else {
                return "Exact text match";
            }
        }
    }
    
    public static FilterSuggestion analyzeNotification(String appName, String packageName, String notificationText) {
        return new FilterSuggestion(notificationText, appName, packageName);
    }
    
    public static String createFilterRule(FilterSuggestion suggestion, FilterType filterType) {
        switch (filterType) {
            case EXACT:
                return suggestion.exactMatch;
            case PATTERN:
                return suggestion.patternMatch;
            case KEYWORDS:
                return suggestion.keywordMatch;
            case APP_SPECIFIC:
                return "*"; // Special marker for app-specific filters
            default:
                return suggestion.patternMatch;
        }
    }
    
    public enum FilterType {
        EXACT("Exact match"),
        PATTERN("Smart pattern (recommended)"),
        KEYWORDS("Keywords only"),
        APP_SPECIFIC("All notifications from this app");
        
        public final String displayName;
        
        FilterType(String displayName) {
            this.displayName = displayName;
        }
    }
    
    // Test if a notification matches a filter rule
    public static boolean matchesFilter(String notificationText, String filterRule, FilterType filterType) {
        switch (filterType) {
            case EXACT:
                return notificationText.equals(filterRule);
            case PATTERN:
                return matchesPattern(notificationText, filterRule);
            case KEYWORDS:
                return containsKeywords(notificationText, filterRule);
            case APP_SPECIFIC:
                return true; // App filtering is handled elsewhere
            default:
                return false;
        }
    }
    
    private static boolean matchesPattern(String text, String pattern) {
        // Convert pattern back to regex for matching
        String regex = Pattern.quote(pattern);
        regex = regex.replace("\\[TIME\\]", "\\\\E\\\\b\\\\d{1,2}:\\\\d{2}(:\\\\d{2})?\\\\s*(AM|PM|am|pm)?\\\\b\\\\Q");
        regex = regex.replace("\\[DATE\\]", "\\\\E\\\\b\\\\d{1,2}[/-]\\\\d{1,2}[/-]\\\\d{2,4}\\\\b\\\\Q");
        regex = regex.replace("\\[NUMBER\\]", "\\\\E\\\\b\\\\d+\\\\b\\\\Q");
        regex = regex.replace("\\[PERCENT\\]", "\\\\E\\\\b\\\\d+%\\\\b\\\\Q");
        regex = regex.replace("\\[SIZE\\]", "\\\\E\\\\b\\\\d+(\\\\.\\\\d+)?\\\\s*(MB|GB|KB|TB)\\\\b\\\\Q");
        regex = regex.replace("\\[URL\\]", "\\\\E\\\\bhttps?://[^\\\\s]+\\\\b\\\\Q");
        regex = regex.replace("\\[EMAIL\\]", "\\\\E\\\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\\\.[A-Z|a-z]{2,}\\\\b\\\\Q");
        
        try {
            Pattern compiledPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            return compiledPattern.matcher(text).find();
        } catch (Exception e) {
            // Fallback to simple contains check
            return text.toLowerCase().contains(pattern.toLowerCase());
        }
    }
    
    private static boolean containsKeywords(String text, String keywords) {
        if (keywords.isEmpty()) return false;
        
        String[] keywordArray = keywords.split("\\s+");
        String lowerText = text.toLowerCase();
        
        // All keywords must be present
        for (String keyword : keywordArray) {
            if (!lowerText.contains(keyword.toLowerCase())) {
                return false;
            }
        }
        return true;
    }
} 