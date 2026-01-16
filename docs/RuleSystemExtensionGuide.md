# Rule System Extension Guide

This guide documents how to add new **conditions** (triggers/exceptions) and
**actions** to the SpeakThat rules system, matching the exact style used so far.

---
## Quick Summary

Every new rule feature typically needs:

- Data model updates (`TriggerType`, `ExceptionType`, `ActionType`)
- UI additions (config screens + layouts)
- Strings (all locales)
- Validation rules
- Evaluator logic
- Action execution wiring
- Notification pipeline integration (if it affects reading)

Follow the sections below in order.

---
## 1) Add New Trigger / Exception Types

### Files
- `app/src/main/java/com/micoyc/speakthat/rules/RuleData.kt`

### Steps
1. Add the new enum value to:
   - `TriggerType`
   - `ExceptionType`

2. Set the correct `isCacheable` flag:
   - `false` for notification data or fast-changing data
   - `true` for stable device state

3. Add natural language description handling in:
   - `getSingleTriggerDescription()`
   - `getSingleExceptionDescription()`

4. If the condition can be inverted:
   - Use `inverted` on the Trigger/Exception
   - Ensure the UI includes the "Invert Condition" switch

---
## 2) Add New Action Types

### Files
- `app/src/main/java/com/micoyc/speakthat/rules/RuleData.kt`
- `app/src/main/java/com/micoyc/speakthat/rules/RuleManager.kt`
- `app/src/main/java/com/micoyc/speakthat/rules/ActionExecutor.kt`
- `app/src/main/java/com/micoyc/speakthat/NotificationReaderService.kt` (if it affects reading)

### Steps
1. Add the new enum value to `ActionType`.
2. Add a description in `getSingleActionDescription()`.
3. Map the action to an `Effect` in `RuleManager.mapActionToEffect()`.
4. Aggregate priority in `RuleManager.aggregateEffects()` if needed.
5. Implement execution or "handled by pipeline" stub in `ActionExecutor`.
6. Apply the effect inside `NotificationReaderService` if it changes
   notification processing or speech output.

---
## 3) Add Evaluator Logic

### Files
- `app/src/main/java/com/micoyc/speakthat/rules/RuleEvaluator.kt`

### Steps
1. Add a new evaluator function (e.g., `evaluateForegroundAppTrigger`).
2. Wire it into:
   - `evaluateTrigger()`
   - `evaluateException()`
3. Log clearly:
   - input data
   - computed state
   - final result
4. Respect `inverted` behavior (already handled by the framework).

---
## 4) Add Validation Rules

### Files
- `app/src/main/java/com/micoyc/speakthat/rules/RuleManager.kt`

### Steps
1. Add validation logic inside:
   - `validateTriggerData()`
   - `validateExceptionData()`
   - `validateActionData()` (if needed)
2. Use string resources for validation errors.

---
## 5) Add UI Config

### Trigger UI

#### Files
- `app/src/main/java/com/micoyc/speakthat/rules/TriggerConfigActivity.kt`
- `app/src/main/res/layout/activity_trigger_config.xml`

#### Steps
1. Add a new card layout in `activity_trigger_config.xml`.
2. Add setup + load + create functions in `TriggerConfigActivity`:
   - `setupXxxUI()`
   - Load section in `loadCurrentValues()`
   - Create section in `saveTrigger()`

### Exception UI

#### Files
- `app/src/main/java/com/micoyc/speakthat/rules/ExceptionConfigActivity.kt`
- `app/src/main/res/layout/activity_exception_config.xml`

#### Steps
1. Mirror the trigger UI for exceptions.
2. Add setup + load + create functions in `ExceptionConfigActivity`.

### Action UI

#### Files
- `app/src/main/java/com/micoyc/speakthat/rules/ActionConfigActivity.kt`
- `app/src/main/res/layout/activity_action_config.xml`

#### Steps
1. Add a new action card layout.
2. Add setup + create functions in `ActionConfigActivity`.

---
## 6) Add Menu Entries

### Files
- `app/src/main/java/com/micoyc/speakthat/RuleBuilderActivity.kt`

### Steps
1. Add the trigger/exception/action option to the menu arrays.
2. Add handler functions to launch the config screens.

---
## 7) Strings (All Locales)

### Files
- `app/src/main/res/values/strings.xml`
- All `values-*` locale files

### Required strings
- Title + description
- Labels, hints, and button text
- Natural language descriptions
- Validation errors

Do **not** hardcode UI text.

---
## 8) Permissions (If Needed)

If a condition requires permissions (Bluetooth, WiFi, accessibility, etc.):

- Check permission before launching configuration
- Provide a user-facing warning or toast
- Avoid crashing if missing permission

---
## 9) Caching Rules

Use `isCacheable = false` for any condition that depends on:

- Notification content
- Foreground app
- Volatile state

Keep `isCacheable = true` for:

- Stable device state (charging, Bluetooth, WiFi)

---
## 10) Notification Pipeline Integration

If your action modifies reading behavior:

### Files
- `app/src/main/java/com/micoyc/speakthat/NotificationReaderService.kt`

### Typical integration points
- Rule evaluation before filtering
- Skip notification logic (`Effect.SkipNotification`)
- Override template for custom speech
- Force private / override private

---
## 11) Testing Checklist

Run through:

- Create rule with new condition/action
- Edit and resave the rule
- Rule evaluation logs show expected results
- Effects apply correctly in notification pipeline
- UI looks consistent (light/dark themes)
- Strings appear correctly in all locales

---
## Notes / Existing Conventions

- All switches should use consistent styling.
- Use theme attributes for colors; avoid hardcoded colors.
- Keep UI modular; avoid hard-coded flows.
- No extra battery usage unless clearly justified.

