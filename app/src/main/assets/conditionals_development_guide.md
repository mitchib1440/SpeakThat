# Conditional Filtering System - Development Guide

## Overview

The Conditional Filtering System is a foundation for advanced, context-aware notification filtering in SpeakThat. This system allows users to create rules that automatically adjust notification behavior based on various conditions like time, day of week, app patterns, and notification content.

## 📋 Latest Session Notes - December 2024

### What We Accomplished
1. **Settings Reorganization**: Moved Smart Rules to its own settings category instead of being part of Filter Settings
2. **UI Foundation**: Added Smart Settings card to main settings with clean, professional styling
3. **UI Polish**: Fixed visual overlap issues and removed badge for consistent formatting
4. **Session Documentation**: Added comprehensive notes to key files for future development sessions
5. **Architecture Validation**: Confirmed the foundation is solid and production-ready

### Key Decisions Made
- **Separate Settings Category**: User preferred Smart Settings as its own category vs being in Filter Settings
- **UI Approach**: Simple default interface with "More Info" for advanced features
- **Clean Design**: Removed "Coming Soon" badge for consistent formatting across all settings cards
- **Architecture**: Keep existing foundation as-is - no breaking changes needed

### Files Modified This Session
- `app/src/main/res/layout/activity_settings.xml` - Added Smart Rules card
- `app/src/main/res/layout/activity_filter_settings.xml` - Removed conditionals section
- `app/src/main/java/com/micoyc/speakthat/SettingsActivity.kt` - Added Smart Rules click handler
- `app/src/main/java/com/micoyc/speakthat/ConditionalFilterManager.java` - Added session notes
- `app/src/main/java/com/micoyc/speakthat/NotificationReaderService.kt` - Enhanced integration notes

### Next Session Priorities
1. Create `SmartSettingsActivity.java` (replace toast with real activity)
2. Build visual rule builder UI with dropdowns
3. Implement rule list management (enable/disable/delete)
4. Add quick rule templates ("Work Hours", "Quiet Time", etc.)
5. Test with real-world scenarios
6. Enable conditional filtering in NotificationReaderService

## Architecture

### Core Components

1. **ConditionalFilterManager** (`ConditionalFilterManager.java`)
   - Main orchestrator for conditional rules
   - Handles rule storage, evaluation, and execution
   - Provides JSON-based persistence

2. **Integration Points** (`NotificationReaderService.kt`)
   - Hooks in the notification processing pipeline
   - Placeholder methods for future implementation
   - Ready for seamless integration

3. **UI Foundation** (`activity_filter_settings.xml`)
   - "Coming Soon" section in Filter Settings
   - Preview of upcoming features
   - User education and expectation setting

## Data Structures

### ConditionalRule
```java
public static class ConditionalRule {
    public String id;                    // Unique identifier
    public String name;                  // User-friendly name
    public String description;           // Detailed description
    public boolean enabled;              // Active/inactive state
    public int priority;                 // Execution order (higher = first)
    public List<Condition> conditions;   // What triggers the rule
    public List<Action> actions;         // What the rule does
    public String createdDate;           // Creation timestamp
    public String lastModified;          // Last edit timestamp
}
```

### Condition Types
- **TIME_OF_DAY**: "09:00-17:00", ">22:00", "=12:30"
- **DAY_OF_WEEK**: "monday", "weekday", "weekend", "monday,friday"
- **APP_PACKAGE**: Package name matching/containing
- **NOTIFICATION_CONTENT**: Text content analysis
- **NOTIFICATION_COUNT**: Frequency-based rules
- **LAST_NOTIFICATION_TIME**: Time since last notification
- **DEVICE_STATE**: (Future) Charging, headphones, etc.
- **LOCATION**: (Future) Home, work, etc.

### Action Types
- **BLOCK_NOTIFICATION**: Prevent notification from being read
- **MAKE_PRIVATE**: Replace content with "[PRIVATE]"
- **SET_DELAY**: Override delay before readout
- **CHANGE_BEHAVIOR**: Modify notification behavior mode
- **ADD_TO_FILTER**: Auto-create permanent filters
- **MODIFY_TEXT**: Replace or modify notification text
- **SET_PRIORITY**: Mark as priority/non-priority
- **LOG_EVENT**: Debug and analytics logging

### Comparison Operators
- **EQUALS**: Exact match
- **NOT_EQUALS**: Not equal to
- **CONTAINS**: Text contains substring
- **NOT_CONTAINS**: Text does not contain
- **GREATER_THAN**: Numeric/time comparison
- **LESS_THAN**: Numeric/time comparison
- **MATCHES_PATTERN**: Regex/pattern matching
- **IN_RANGE**: Within specified range

## Example Rules

### 1. Work Hours Focus
```java
ConditionalRule workRule = new ConditionalRule();
workRule.name = "Work Hours Focus";
workRule.description = "Make social media private during work hours";
workRule.priority = 10;

// Conditions: Weekdays AND 9 AM - 5 PM AND social media apps
workRule.conditions.add(new Condition(DAY_OF_WEEK, "", EQUALS, "weekday"));
workRule.conditions.add(new Condition(TIME_OF_DAY, "", IN_RANGE, "09:00-17:00"));
workRule.conditions.add(new Condition(APP_PACKAGE, "", CONTAINS, "facebook"));

// Actions: Make private and log
workRule.actions.add(new Action(MAKE_PRIVATE, "", "true"));
workRule.actions.add(new Action(LOG_EVENT, "", "Work focus applied"));
```

### 2. Evening Quiet Time
```java
ConditionalRule eveningRule = new ConditionalRule();
eveningRule.name = "Evening Quiet Time";
eveningRule.description = "Add delay to notifications after 10 PM";
eveningRule.priority = 5;

// Condition: After 10 PM
eveningRule.conditions.add(new Condition(TIME_OF_DAY, "", GREATER_THAN, "22:00"));

// Action: 3 second delay
eveningRule.actions.add(new Action(SET_DELAY, "", "3"));
```

### 3. Spam Detection
```java
ConditionalRule spamRule = new ConditionalRule();
spamRule.name = "Auto Spam Filter";
spamRule.description = "Block repetitive notifications";
spamRule.priority = 15;

// Conditions: Same app + high frequency
spamRule.conditions.add(new Condition(NOTIFICATION_COUNT, "", GREATER_THAN, "5"));
spamRule.conditions.add(new Condition(LAST_NOTIFICATION_TIME, "", LESS_THAN, "30"));

// Actions: Block and auto-filter
spamRule.actions.add(new Action(BLOCK_NOTIFICATION, "", "true"));
spamRule.actions.add(new Action(ADD_TO_FILTER, "", "auto"));
```

## Implementation Roadmap

### Phase 1: Basic Time/Day Rules ✅ (Foundation Complete)
- [x] Core architecture and data structures
- [x] JSON persistence system
- [x] Time and day condition evaluation
- [x] Basic action execution
- [x] Integration hooks in NotificationReaderService

### Phase 2: UI Implementation (Next Session)
- [ ] Rule creation/editing interface
- [ ] Visual rule builder with dropdowns
- [ ] Rule list management (enable/disable/delete)
- [ ] Import/export functionality
- [ ] Quick rule templates

### Phase 3: Advanced Conditions (Future)
- [ ] App behavior pattern analysis
- [ ] Notification content intelligence
- [ ] Device state integration
- [ ] Location-based rules
- [ ] Machine learning suggestions

### Phase 4: Smart Features (Future)
- [ ] Auto-learning from user filter actions
- [ ] Predictive rule suggestions
- [ ] A/B testing for rule effectiveness
- [ ] Advanced analytics and insights

## Integration Points

### Current Hooks in NotificationReaderService

1. **applyFilters()** method:
   ```kotlin
   // 3. Apply conditional rules (foundation for future advanced filtering)
   // TODO: Future enhancement - apply conditional rules here
   // val conditionalResult = applyConditionalFiltering(packageName, appName, wordFilterResult.processedText)
   ```

2. **applyConditionalFiltering()** placeholder method:
   - Ready for implementation
   - Documented expected behavior
   - Integration with existing FilterResult system

3. **ConditionalFilterManager instance**:
   - Initialized in onCreate()
   - Ready for rule evaluation

### Future Integration Points

1. **Delay Modification**: Override `delayBeforeReadout` based on rules
2. **Behavior Changes**: Modify `notificationBehavior` dynamically
3. **Priority Adjustment**: Add/remove apps from `priorityApps`
4. **Auto-Filter Creation**: Add rules to existing filter lists
5. **TTS Modification**: Change voice, speed, or content

## Storage Format

Rules are stored in SharedPreferences as JSON:

```json
{
  "version": "1.0",
  "lastModified": "2024-01-15 14:30:00",
  "rules": [
    {
      "id": "rule_1705321800000",
      "name": "Work Hours Focus",
      "description": "Make social media private during work hours",
      "enabled": true,
      "priority": 10,
      "createdDate": "2024-01-15 14:30:00",
      "lastModified": "2024-01-15 14:30:00",
      "conditions": [
        {
          "type": "DAY_OF_WEEK",
          "parameter": "",
          "operator": "EQUALS",
          "value": "weekday"
        },
        {
          "type": "TIME_OF_DAY",
          "parameter": "",
          "operator": "IN_RANGE",
          "value": "09:00-17:00"
        }
      ],
      "actions": [
        {
          "type": "MAKE_PRIVATE",
          "parameter": "",
          "value": "true"
        }
      ]
    }
  ]
}
```

## Testing Strategy

### Unit Tests (Future)
- Condition evaluation logic
- Action execution
- JSON serialization/deserialization
- Time parsing and comparison

### Integration Tests (Future)
- End-to-end rule application
- Performance with multiple rules
- Edge cases and error handling

### User Testing (Future)
- Rule creation workflow
- Understanding of condition/action concepts
- Effectiveness of common use cases

## Performance Considerations

### Current Design
- Rules evaluated in priority order
- Early exit on first blocking rule
- Minimal object creation during evaluation
- Cached rule parsing

### Future Optimizations
- Rule indexing by condition type
- Lazy evaluation of expensive conditions
- Background rule compilation
- Performance metrics and monitoring

## Error Handling

### Current Approach
- Graceful degradation on invalid rules
- Logging of evaluation errors
- Fallback to default behavior
- JSON parsing error recovery

### Future Enhancements
- Rule validation before save
- User-friendly error messages
- Rule debugging tools
- Automatic rule correction

## Security Considerations

### Data Protection
- Rules stored in app-private storage
- No sensitive data in rule definitions
- Export/import validation
- Version compatibility checks

### Future Considerations
- Rule sharing validation
- Cloud sync security
- Privacy-preserving analytics
- User consent for data collection

## Development Notes

### Code Style
- Follow existing SpeakThat conventions
- Comprehensive JavaDoc comments
- Defensive programming practices
- Clear variable and method names

### Dependencies
- No additional external libraries required
- Uses existing JSON parsing
- Leverages current SharedPreferences system
- Compatible with existing filter architecture

### Backwards Compatibility
- New features are additive only
- Graceful handling of missing rules
- Version migration support
- Default behavior preservation

## Quick Start for Next Session

1. **Enable Conditional Filtering**:
   ```kotlin
   // In applyFilters(), uncomment the conditional filtering line:
   val conditionalResult = applyConditionalFiltering(packageName, appName, wordFilterResult.processedText)
   ```

2. **Create Basic UI**:
   - Replace "Coming Soon" section with basic rule list
   - Add "Add Rule" button
   - Implement simple rule creation dialog

3. **Test with Example Rules**:
   ```java
   // In development mode, create example rules:
   conditionalFilterManager.createExampleRules()
   ```

4. **Add Settings Integration**:
   - Link to conditional rules from main settings
   - Add enable/disable toggle
   - Implement rule import/export

This foundation provides a solid base for building sophisticated conditional filtering while maintaining the app's simplicity and performance characteristics. 