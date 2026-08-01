# Settings Search Indexing Guide

This guide explains how SpeakThat’s in-app settings search works, and how to
index a new setting (or an entire settings page) so it appears in search results
and scrolls/highlights correctly when tapped.

Use this document whenever you add a new toggle, spinner, card, or settings
screen — and when asking an AI assistant to index settings later.

---

## Quick Summary

Every searchable setting needs **three coordinated pieces**, plus string
resources:

| Piece | File | Purpose |
|---|---|---|
| Search entry | `SettingsDatabase.kt` | Title, description, category, keywords, navigation |
| Highlight map | `SettingsHighlightHelper.kt` | Maps setting `id` → layout `R.id.*` for scroll/flash |
| Search strings | `search_settings_strings.xml` | `search_title_*`, `search_desc_*`, `search_keywords_*` |
| Layout id (if missing) | Relevant `activity_*.xml` | Stable `android:id` on the control or card |
| Scroll hook (new screens only) | Settings activity | Call `SettingsHighlightHelper.handleScrollToSetting(this)` |

**The `id` string must match exactly** in `SettingsDatabase` and
`SettingsHighlightHelper`.

---

## How Search Works

### Flow

1. User types a query in settings search.
2. `SettingsDatabase.getAllSettings()` builds the full index (filtered by
   distribution flavour).
3. `SettingsSearchEngine.search()` ranks matches against title, description, and
   keywords.
4. Results are grouped by category and shown in the UI.
5. Tapping a result runs that item’s `navigationAction`, which usually starts the
   target activity with:

   ```text
   Intent.putExtra("SCROLL_TO_SETTING", id)
   ```

6. The target activity calls `SettingsHighlightHelper.handleScrollToSetting(this)`,
   which:
   - Looks up `id` in `VIEW_IDS`
   - Finds the view
   - Scrolls the nearest `ScrollView` / `NestedScrollView` to it
   - Briefly flashes a highlight on the control (or its padded parent row)

### Matching tiers (`SettingsSearchEngine`)

| Tier | Match type |
|---|---|
| 1 | Query equals or is contained in the **title** |
| 2 | Query equals/contained in **description** or **keywords** (also if query contains a keyword) |
| 3 | Levenshtein distance of exactly 1 against a title word or keyword (typo tolerance) |

### Implications for authors

- Prefer search titles that match the **on-screen UI label** (users search what
  they see).
- Put synonyms, alternate spellings, and related terms in **keywords**.
- Keywords are a **comma-separated** list in one string resource.
- Use British spelling in user-facing English where the app already does
  (e.g. **Behaviour**, **Honour**, **Customise**).

---

## Key Files

| Path | Role |
|---|---|
| `app/src/main/java/com/micoyc/speakthat/SettingsDatabase.kt` | Master list of `SettingsItem`s |
| `app/src/main/java/com/micoyc/speakthat/SettingsHighlightHelper.kt` | `VIEW_IDS` map + scroll/highlight |
| `app/src/main/java/com/micoyc/speakthat/SettingsItem.kt` | Data model + `SettingType` enum |
| `app/src/main/java/com/micoyc/speakthat/SettingsSearchEngine.kt` | Ranking / matching |
| `app/src/main/res/values/search_settings_strings.xml` | Search-only strings (titles, descs, keywords, categories) |
| Settings layouts under `app/src/main/res/layout/` | Must expose `android:id`s for highlight targets |

---

## Step-by-step: Index One Setting

### 1) Choose a stable `id`

Use `snake_case`, unique across the whole index.

Examples:

- `auto_language`
- `toast_main_app`
- `summary_speech_pacing`
- `clock_interval_15`

Conventions used in this project:

- Individual control: descriptive name (`shake_intensity`, `force_lowercase`)
- Whole card/section: often ends with `_section` (`style_section`,
  `content_cap_section`, `toast_notifications_section`)
- Prefix with feature area when helpful (`summary_*`, `clock_*`, `tidy_speech_*`)

Do **not** rename existing ids casually — anything that deep-links with
`SCROLL_TO_SETTING` depends on them.

### 2) Ensure the layout has a highlightable id

Open the settings screen layout and find the control users should land on.

| What you’re indexing | Prefer highlighting |
|---|---|
| Switch / checkbox | The `MaterialSwitch` / `Switch` id |
| SeekBar / Slider | The seek/slider id |
| Spinner | The spinner id |
| Radio option | The specific `RadioButton` id (or the `RadioGroup` if indexing the whole group) |
| Button / clickable row | The button or row container id |
| Entire card/section | The `MaterialCardView` id (add one if missing) |

If the card/section has no id, add one:

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardMyNewSection"
    ... >
```

If the control is nested in a small switch with little padding, the highlight
helper walks up to a padded parent row automatically — still map to the
**control** id, not a random ancestor, unless you intentionally want the whole
card.

### 3) Add search strings

In `app/src/main/res/values/search_settings_strings.xml`, add three strings
(plus a category string if this is a brand-new settings page):

```xml
<string name="search_title_my_setting">My Setting Label</string>
<string name="search_desc_my_setting">Short explanation of what it does</string>
<string name="search_keywords_my_setting">keyword1, keyword2, synonym, related, term</string>
```

Guidance:

- **Title**: Match the UI label as closely as possible.
- **Description**: One short sentence; this is shown in search results and used
  for matching.
- **Keywords**: Lowercase is fine (matching lowercases everything). Include:
  - Exact UI words
  - Common synonyms (“toast”, “popup”)
  - Related concepts (“battery”, “deduplication”)
  - Alternate spellings if useful (`customise, customize`)
- Prefer putting search copy in `search_settings_strings.xml` rather than
  reusing UI strings, so search wording can differ slightly from on-screen help
  text when needed.
- You *may* reuse existing UI string resources for `titleRes` /
  `descriptionRes` (some tidy-speech / swipe entries do). Either approach is
  fine; stay consistent within a feature area.

### 4) Add a `SettingsItem` in `SettingsDatabase.kt`

Place it near related settings for the same screen/category.

```kotlin
SettingsItem(
    id = "my_setting",
    titleRes = R.string.search_title_my_setting,
    descriptionRes = R.string.search_desc_my_setting,
    category = "general", // machine key; see Categories below
    categoryTitleRes = R.string.search_cat_general,
    categoryIconRes = R.drawable.ic_mobile_gear_24,
    settingType = SettingType.SWITCH,
    searchKeywordsRes = R.string.search_keywords_my_setting,
    navigationAction = { context, id ->
        context.startActivity(
            Intent(context, GeneralSettingsActivity::class.java)
                .putExtra("SCROLL_TO_SETTING", id)
        )
    }
),
```

#### `SettingType` values

From `SettingsItem.kt`:

| Type | Typical use |
|---|---|
| `SWITCH` | Toggles |
| `SEEK_BAR` | Sliders / seek bars |
| `SPINNER` | Dropdowns |
| `BUTTON` | Buttons or tap-to-open rows |
| `TEXT_INPUT` | Text fields |
| `RADIO_GROUP` | Radio groups or individual radio options |
| `CARD` | Whole card/section entries |

The type is mainly for classification/display; navigation still depends on your
`navigationAction` and highlight map.

#### Flavour gating (`supportedFlavors`)

Default is all channels: `github`, `store`, `play`.

Restrict when a setting is unavailable on some builds:

```kotlin
supportedFlavors = listOf("github"),              // e.g. auto-updates
supportedFlavors = listOf("github", "store"),     // e.g. Press to Stop (no accessibility on play)
```

`getAllSettings()` filters with:

```kotlin
.filter { currentFlavor in it.supportedFlavors }
```

where `currentFlavor` is `BuildConfig.DISTRIBUTION_CHANNEL`.

#### Advanced / collapsed UI

Some Voice settings live behind an “advanced” expand section. Those intents also
pass `expand_advanced`:

```kotlin
navigationAction = { context, id ->
    val intent = Intent(context, VoiceSettingsActivity::class.java)
    intent.putExtra("expand_advanced", true)
    intent.putExtra("SCROLL_TO_SETTING", id)
    context.startActivity(intent)
}
```

If a new setting is hidden until the user expands a section, make sure
navigation expands it first (or the highlight target may be gone / `GONE`).

### 5) Map the id in `SettingsHighlightHelper.kt`

Add one entry to `VIEW_IDS`, in the comment section for that screen:

```kotlin
"my_setting" to R.id.switchMySetting,
```

Rules:

- Key = exact `SettingsItem.id`
- Value = the layout view you chose in step 2
- Keep sections grouped (`// General`, `// Behavior`, `// Summary`, etc.)

### 6) Wire scroll handling on the activity (new screens only)

Near the end of `onCreate` (after views are inflated/bound):

```kotlin
SettingsHighlightHelper.handleScrollToSetting(this)
```

Most settings activities already call this. New activities **must** call it or
search will open the page but never scroll/highlight.

The intent extra key is `"SCROLL_TO_SETTING"`
(`SettingsHighlightHelper.EXTRA_SCROLL_TO_SETTING`).

---

## Indexing a Whole Card / Section

When a **card title** should be searchable (even if individual rows are also
indexed):

1. Give the `MaterialCardView` an id (e.g. `cardNotificationHistory`).
2. Add a `SettingsItem` with `settingType = SettingType.CARD`.
3. Map the item id to that card id in `VIEW_IDS`.
4. Optionally still index children as separate items (recommended when users
   search for specific row labels).

Examples already in the codebase:

- `style_section` → `cardBadgeSettings`
- `toast_notifications_section` → `cardToastNotifications`
- `content_cap_section` → `cardContentCap`
- `summary_notification_order` → `cardNotificationOrder`
- `bluetooth_phone_call_simulation` → `cardBluetoothPhoneCallSimulation`

If you only index the card, searching a child label may fail unless keywords
cover those child terms.

---

## Indexing an Entire New Settings Page

When adding a brand-new settings activity:

1. **Create a category**
   - Add `search_cat_my_page` in `search_settings_strings.xml`
   - Pick a stable machine `category` string (e.g. `"summary"`, `"clock"`)
   - Choose a `categoryIconRes` drawable used consistently for that page

2. **Index every user-visible option** (and cards that should be searchable)

3. **Import the activity** in `SettingsDatabase.kt` if needed

4. **Call** `SettingsHighlightHelper.handleScrollToSetting(this)` in the
   activity’s `onCreate`

5. **Add all** `VIEW_IDS` entries under a new comment block in
   `SettingsHighlightHelper.kt`

Recent full-page examples: Summary Settings and Clock Settings.

---

## Categories Currently Used

| `category` key | Typical `categoryTitleRes` | Typical icon |
|---|---|---|
| `general` | `search_cat_general` | `ic_mobile_gear_24` |
| `behavior` | `search_cat_behavior` (“Behaviour Settings”) | `ic_notification_settings_24` |
| `voice` | `search_cat_voice` | `ic_voice_selection_24` |
| `filter` | `title_filter_settings` (note: not `search_cat_*`) | `ic_filter_list_24` |
| `summary` | `search_cat_summary` | `ic_slideshow_24` |
| `clock` | `search_cat_clock` | `ic_alarm_clock_24` |
| `conditional` | `search_cat_conditional` | `ic_bluetooth_24` |
| `compatibility` | `search_cat_compatibility` | `ic_framebug_24` |
| `development` | `search_cat_development` | `ic_code_24` / `ic_bugdroid_24` |
| `support` | `search_cat_support` | (support icon used by those entries) |
| `onboarding` | `search_cat_onboarding` | `ic_laps_24` |

When adding a category, keep the machine key short and stable; only the title
string is user-facing.

---

## Checklist (copy/paste)

```text
[ ] Stable snake_case id chosen
[ ] Layout has android:id on highlight target (control or card)
[ ] search_title_*, search_desc_*, search_keywords_* added
[ ] New search_cat_* added if this is a new page/category
[ ] SettingsItem added in SettingsDatabase.kt (correct category + activity)
[ ] supportedFlavors set if not available on all builds
[ ] expand_advanced (or similar) passed if UI is collapsed by default
[ ] VIEW_IDS entry added in SettingsHighlightHelper.kt (same id)
[ ] Activity calls SettingsHighlightHelper.handleScrollToSetting(this)
[ ] Manual test: search by UI title → result appears → tap → scrolls + flashes
[ ] Manual test: search by a keyword synonym
[ ] Manual test on gated flavours if applicable (e.g. play vs github)
```

---

## Common Pitfalls

1. **Id mismatch** between database and highlight map → opens activity, no scroll.
2. **Missing layout id** → highlight lookup fails silently.
3. **Activity never calls** `handleScrollToSetting` → same as above.
4. **Target view is `GONE`** (collapsed advanced section, disabled feature card,
   flavour-hidden UI) → scroll finds nothing. Expand/show first, or don’t index
   for that flavour.
5. **Indexing only the parent mode group** while users search for a later-added
   child label (e.g. Word Whitelist) → add an explicit child entry and/or
   keywords.
6. **Titles that don’t match the UI** → users think the setting isn’t indexed.
7. **Empty or thin keywords** → only exact title fragments match well.
8. **Forgetting flavour filters** → search shows Play-only-hidden features.

---

## Minimal End-to-end Example

Indexing a fictional General Settings switch `switchWidgetMode`:

**Layout** (`activity_general_settings.xml`):

```xml
<com.google.android.material.materialswitch.MaterialSwitch
    android:id="@+id/switchWidgetMode"
    ... />
```

**Strings** (`search_settings_strings.xml`):

```xml
<string name="search_title_widget_mode">Widget Mode</string>
<string name="search_desc_widget_mode">Show SpeakThat status on the home-screen widget</string>
<string name="search_keywords_widget_mode">widget, mode, home, screen, status, launcher</string>
```

**Database** (`SettingsDatabase.kt`):

```kotlin
SettingsItem(
    id = "widget_mode",
    titleRes = R.string.search_title_widget_mode,
    descriptionRes = R.string.search_desc_widget_mode,
    category = "general",
    categoryTitleRes = R.string.search_cat_general,
    categoryIconRes = R.drawable.ic_mobile_gear_24,
    settingType = SettingType.SWITCH,
    searchKeywordsRes = R.string.search_keywords_widget_mode,
    navigationAction = { context, id ->
        context.startActivity(
            Intent(context, GeneralSettingsActivity::class.java)
                .putExtra("SCROLL_TO_SETTING", id)
        )
    }
),
```

**Highlight map** (`SettingsHighlightHelper.kt`):

```kotlin
"widget_mode" to R.id.switchWidgetMode,
```

That’s all three pieces. No activity change needed if
`GeneralSettingsActivity` already calls `handleScrollToSetting`.

---

## For AI Assistants / Future Indexing Passes

When asked to index missing settings:

1. Read this document first.
2. Work **page by page** (General → Behaviour → Voice → Filter → …).
3. For each missing item:
   - Locate the layout control / card id (add card ids when whole cards are
     missing from search).
   - Add strings, `SettingsItem`, and `VIEW_IDS` together.
   - Respect British spelling already used in Behaviour/Honour copy.
   - Apply `supportedFlavors` when the UI is flavour-gated
     (`BuildConfig.HAS_ACCESSIBILITY`, updater-only features, etc.).
4. If the user marks **“The card itself needs indexing”**, add a `CARD` entry
   pointing at the card view id, not only the first child control.
5. If a setting appears missing but already exists under a different title,
   prefer updating the search title/keywords to match the UI rather than
   duplicating entries — unless a distinct child option truly lacks its own
   index row.
6. Do not invent highlight targets; verify `R.id` exists in the layout.
7. After indexing a brand-new activity, always wire
   `SettingsHighlightHelper.handleScrollToSetting`.

---

## Related Code References

- Intent extra: `SettingsHighlightHelper.EXTRA_SCROLL_TO_SETTING` (`"SCROLL_TO_SETTING"`)
- Filter at end of index build: `SettingsDatabase.getAllSettings()` →
  `.filter { currentFlavor in it.supportedFlavors }`
- Highlight flash colour: `R.color.purple_200` (semi-transparent flash animation)
