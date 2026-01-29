# SpeakThat Translation Instructions for Weblate

## Overview
This document provides comprehensive instructions for translating the SpeakThat app using Weblate. SpeakThat is an Android notification reader app that reads notifications aloud using text-to-speech.

## Project Structure
- **Source language**: English (en)
- **Target languages**: Multiple (see supported languages below)
- **File location**: `app/src/main/res/values-{language_code}/strings.xml`
- **Source file**: `app/src/main/res/values/strings.xml`

## Supported Languages
The app currently supports these languages (in addition to English):
- Arabic (ar)
- Chinese Simplified (zh-rCN)
- Chinese Traditional (zh-rTW)
- Dutch (nl)
- French (fr)
- German (de)
- Hindi (hi)
- Indonesian (id)
- Italian (it)
- Japanese (ja)
- Korean (ko)
- Polish (pl)
- Portuguese (pt)
- Punjabi (pa-rIN)
- Russian (ru)
- Spanish (es)
- Swedish (sv)
- Thai (th)
- Turkish (tr)
- Vietnamese (vi)

## Translation Guidelines

### 1. General Principles
- **Preserve meaning**: Ensure the translated text conveys the same meaning as the English original
- **Maintain functionality**: Don't change any technical terms, placeholders, or formatting
- **Keep it simple**: Use clear, straightforward language that users will understand immediately
- **Be consistent**: Use consistent terminology throughout the app for similar concepts

### 2. Technical Requirements

#### Placeholders
- **NEVER translate or modify these placeholders**: `{app}`, `{title}`, `{text}`, `{package}`, `{count}`, `{time}`, `{device}`, `{network}`
- **Examples**:
  - `"Reading notification from {app}"` → `"Leyendo notificación de {app}"` (Spanish)
  - `"Connected to {device}"` → `"Conectado a {device}"` (Spanish)

#### HTML Tags
- **Preserve all HTML formatting tags exactly as they appear**:
  - `<b>text</b>` for bold text
  - `<i>text</i>` for italic text
  - `<br>` for line breaks
- **Examples**:
  - `"<b>Interrupt</b> - Stops current notification"` → `"<b>Interrumpir</b> - Detiene la notificación actual"`

#### Special Characters
- **Preserve apostrophes and quotes**: Use appropriate typographic quotes for your language
- **Maintain spacing**: Don't add or remove spaces around punctuation unless required by your language

### 3. Content-Specific Guidelines

#### App Names and Technical Terms
- **App names**: Translate common app names (e.g., "Twitter" → "X" if appropriate for your region)
- **Technical terms**: Use standard translations for Android/tech terms in your language
- **Brand names**: Generally keep brand names as-is unless there's a widely accepted translation

#### UI Elements
- **Buttons**: Keep translations short and action-oriented
- **Settings**: Use clear, descriptive language
- **Error messages**: Be helpful and specific
- **Success messages**: Be encouraging but not overly verbose

#### Notification Content
- **Context matters**: Consider that these strings are read aloud
- **Natural speech**: Use language that sounds natural when spoken
- **Clarity**: Avoid ambiguous language that could confuse users

### 4. Language-Specific Considerations

#### Right-to-Left Languages (Arabic, Hebrew)
- **Layout**: The app automatically handles RTL layout
- **Text direction**: Ensure proper text flow in your translations

#### Asian Languages (Chinese, Japanese, Korean)
- **Formality**: Use appropriate formality level for your target audience
- **Technical terms**: Use established technical terminology in your language
- **Character spacing**: Ensure proper spacing and readability

#### European Languages
- **Formal vs Informal**: Use appropriate formality level (formal is generally safer)
- **Technical terms**: Use standard technical translations
- **Regional variations**: Consider regional differences (e.g., Spanish from Spain vs Latin America)

### 5. Quality Checklist

Before submitting a translation, verify:
- [ ] All placeholders are preserved exactly
- [ ] HTML tags are intact
- [ ] No extra spaces or characters were added
- [ ] The meaning matches the English original
- [ ] The text sounds natural in your language
- [ ] Technical terms are correctly translated
- [ ] No placeholder text was accidentally translated

### 6. Common Translation Patterns

#### Settings and Options
- **English**: "Enable feature"
- **Spanish**: "Activar función"
- **German**: "Funktion aktivieren"
- **French**: "Activer la fonction"

#### Status Messages
- **English**: "Service enabled"
- **Spanish**: "Servicio activado"
- **German**: "Dienst aktiviert"
- **French**: "Service activé"

#### Error Messages
- **English**: "Permission required"
- **Spanish**: "Permiso requerido"
- **German**: "Berechtigung erforderlich"
- **French**: "Permission requise"

### 7. Weblate-Specific Instructions

#### Component Setup
- **Component name**: `SpeakThat Android App`
- **File mask**: `app/src/main/res/values-*/strings.xml`
- **Source language**: English
- **Source file**: `app/src/main/res/values/strings.xml`

#### Translation Workflow
1. **Review**: Check the English source text carefully
2. **Translate**: Provide accurate translation in your language
3. **Review**: Double-check your translation against guidelines
4. **Submit**: Submit your translation for review
5. **Update**: Update translations when source text changes

#### Quality Assurance
- **Peer review**: Have other native speakers review your translations
- **Context testing**: Test translations in the actual app if possible
- **Consistency**: Use the same terms for similar concepts throughout

### 8. Contact and Support

For questions about translations:
- **Technical issues**: Report through Weblate issue tracker
- **Translation questions**: Ask in the project discussion area
- **Language-specific help**: Consult with other translators for your language

### 9. Updates and Maintenance

- **Regular updates**: Check for new strings to translate
- **Source changes**: Update translations when English text changes
- **Quality improvements**: Continuously improve existing translations
- **Community feedback**: Incorporate user feedback on translations

## Example Translations

### Good Translation Example
**English**: `"Enable notification reading"`
**Spanish**: `"Activar lectura de notificaciones"`
**German**: `"Benachrichtigungslesung aktivieren"`

### Bad Translation Example (What to Avoid)
**English**: `"Enable notification reading"`
**Spanish**: `"Enable notification reading"` (not translated)
**German**: `"Enable notification reading"` (not translated)

### Placeholder Preservation Example
**English**: `"Reading from {app}"`
**Spanish**: `"Leyendo desde {app}"` ✅
**Spanish**: `"Leyendo desde aplicación"` ❌ (placeholder removed)

Remember: The goal is to make SpeakThat accessible and user-friendly for speakers of your language while maintaining the app's functionality and technical integrity.

