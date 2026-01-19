package com.micoyc.speakthat.rules.migration

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.rules.Action
import com.micoyc.speakthat.rules.ActionTypeAdapter
import com.micoyc.speakthat.rules.ExceptionType
import com.micoyc.speakthat.rules.ExceptionTypeAdapter
import com.micoyc.speakthat.rules.MapStringAnyTypeAdapter
import com.micoyc.speakthat.rules.Rule
import com.micoyc.speakthat.rules.RuleManager
import com.micoyc.speakthat.rules.RuleTypeAdapter
import com.micoyc.speakthat.rules.Trigger
import com.micoyc.speakthat.rules.TriggerType
import com.micoyc.speakthat.rules.TriggerTypeAdapter

data class RuleMigrationSummary(
    val totalRules: Int,
    val migratedRules: Int,
    val attentionRules: Int,
    val legacyFound: Boolean,
    val dismissed: Boolean
)

object RuleMigrationManager {
    private const val TAG = "RuleMigration"

    private const val RULES_PREFS = "SpeakThatRules"
    private const val RULES_KEY = "rules_list"

    private const val MIGRATION_PREFS = "RuleMigrationPrefs"
    private const val KEY_MIGRATION_DONE = "rules_migration_done"
    private const val KEY_MIGRATION_DISMISSED = "rules_migration_dismissed"
    private const val KEY_LEGACY_FOUND = "rules_migration_legacy_found"
    private const val KEY_MIGRATED_COUNT = "rules_migration_migrated_rules"
    private const val KEY_ATTENTION_COUNT = "rules_migration_attention_rules"
    private const val KEY_TOTAL_RULES = "rules_migration_total_rules"
    private const val KEY_LAST_RULES_HASH = "rules_migration_last_rules_hash"
    private const val LEGACY_ACTION_DISABLE_SPEAKTHAT = "DISABLE_SPEAKTHAT"
    private const val LEGACY_FIELD_CONDITIONS = "conditions"
    private const val LEGACY_FIELD_CONDITION_LOGIC = "conditionLogic"

    fun runIfNeeded(context: Context) {
        val rulesPrefs = context.getSharedPreferences(RULES_PREFS, Context.MODE_PRIVATE)
        val migrationPrefs = getMigrationPrefs(context)

        val rulesJson = rulesPrefs.getString(RULES_KEY, "[]") ?: "[]"
        val rulesHash = rulesJson.hashCode()
        val migrationDone = migrationPrefs.getBoolean(KEY_MIGRATION_DONE, false)
        val lastRulesHash = migrationPrefs.getInt(KEY_LAST_RULES_HASH, 0)

        val legacyMarkersPresent = containsLegacyMarkers(rulesJson)
        if (migrationDone && lastRulesHash == rulesHash && !legacyMarkersPresent) {
            return
        }

        val trimmedJson = rulesJson.trim()
        if (trimmedJson.isEmpty() || trimmedJson == "[]") {
            storeSummary(
                migrationPrefs = migrationPrefs,
                totalRules = 0,
                migratedRules = 0,
                attentionRules = 0,
                legacyFound = false,
                rulesHash = rulesHash
            )
            InAppLogger.logDebug(TAG, "No rules found; migration not needed")
            return
        }

        val knownTriggers = TriggerType.values().map { it.name }.toSet()
        val knownExceptions = ExceptionType.values().map { it.name }.toSet()
        val knownActions = com.micoyc.speakthat.rules.ActionType.values().map { it.name }.toSet()

        var legacyFound = legacyMarkersPresent
        var migratedRulesCount = 0
        val attentionRuleIndexes = mutableSetOf<Int>()

        val jsonArray = try {
            JsonParser.parseString(trimmedJson).asJsonArray
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Failed to parse rules JSON: ${e.message}")
            storeSummary(
                migrationPrefs = migrationPrefs,
                totalRules = 0,
                migratedRules = 0,
                attentionRules = 1,
                legacyFound = false,
                rulesHash = rulesHash
            )
            return
        }

        for ((index, ruleElement) in jsonArray.withIndex()) {
            if (!ruleElement.isJsonObject) {
                attentionRuleIndexes.add(index)
                continue
            }

            val ruleObject = ruleElement.asJsonObject
            var ruleNeedsAttention = false
            var ruleMigrated = false

            val triggersElement = if (ruleObject.has("triggers")) {
                ruleObject.get("triggers")
            } else if (ruleObject.has(LEGACY_FIELD_CONDITIONS)) {
                val legacyConditions = ruleObject.get(LEGACY_FIELD_CONDITIONS)
                ruleObject.add("triggers", legacyConditions)
                ruleObject.remove(LEGACY_FIELD_CONDITIONS)
                legacyFound = true
                ruleMigrated = true
                legacyConditions
            } else {
                null
            }
            val triggers = if (triggersElement != null && triggersElement.isJsonArray) {
                triggersElement.asJsonArray
            } else {
                null
            }
            if (triggers == null) {
                ruleNeedsAttention = true
            } else {
                for (indexInRule in 0 until triggers.size()) {
                    val trigger = triggers[indexInRule]
                    val normalized = normalizeTypedElement(
                        element = trigger,
                        typeKeys = listOf("type", "triggerType", "conditionType"),
                        idPrefix = "trigger"
                    )
                    if (normalized == null) {
                        ruleNeedsAttention = true
                        continue
                    }
                    if (normalized.wasMigrated) {
                        triggers.set(indexInRule, normalized.element)
                        ruleMigrated = true
                        legacyFound = true
                    }
                    val type = normalized.typeName
                    if (type == null || !knownTriggers.contains(type)) {
                        ruleNeedsAttention = true
                    }
                }
            }

            if (ruleObject.has(LEGACY_FIELD_CONDITION_LOGIC) && !ruleObject.has("triggerLogic")) {
                ruleObject.add("triggerLogic", ruleObject.get(LEGACY_FIELD_CONDITION_LOGIC))
                ruleObject.remove(LEGACY_FIELD_CONDITION_LOGIC)
                legacyFound = true
                ruleMigrated = true
            }

            val exceptionsElement = ruleObject.get("exceptions")
            val exceptions = if (exceptionsElement != null && exceptionsElement.isJsonArray) {
                exceptionsElement.asJsonArray
            } else {
                null
            }
            if (exceptions != null) {
                for (indexInRule in 0 until exceptions.size()) {
                    val exception = exceptions[indexInRule]
                    val normalized = normalizeTypedElement(
                        element = exception,
                        typeKeys = listOf("type", "exceptionType"),
                        idPrefix = "exception"
                    )
                    if (normalized == null) {
                        ruleNeedsAttention = true
                        continue
                    }
                    if (normalized.wasMigrated) {
                        exceptions.set(indexInRule, normalized.element)
                        ruleMigrated = true
                        legacyFound = true
                    }
                    val type = normalized.typeName
                    if (type == null || !knownExceptions.contains(type)) {
                        ruleNeedsAttention = true
                    }
                }
            }

            val actionsElement = ruleObject.get("actions")
            val actions = if (actionsElement != null && actionsElement.isJsonArray) {
                actionsElement.asJsonArray
            } else {
                null
            }
            if (actions == null) {
                ruleNeedsAttention = true
            } else {
                for (indexInRule in 0 until actions.size()) {
                    val action = actions[indexInRule]
                    val normalized = normalizeTypedElement(
                        element = action,
                        typeKeys = listOf("type", "actionType"),
                        idPrefix = "action"
                    )
                    if (normalized == null) {
                        ruleNeedsAttention = true
                        continue
                    }
                    val normalizedType = normalizeLegacyActionType(normalized.typeName)
                    val actionObject = normalized.element.asJsonObject
                    var needsDescriptionUpdate = false
                    
                    // Check if description needs updating for SKIP_NOTIFICATION
                    if (normalizedType == "SKIP_NOTIFICATION") {
                        val currentDesc = actionObject.get("description")?.asString ?: ""
                        val expectedDesc = "Don't read this notification aloud"
                        if (currentDesc != expectedDesc && 
                            (currentDesc.contains("Don't read", ignoreCase = true) || 
                             currentDesc.contains("Don't read notifications", ignoreCase = true))) {
                            needsDescriptionUpdate = true
                        }
                    }
                    
                    val finalElement = if (normalizedType != normalized.typeName || needsDescriptionUpdate) {
                        actionObject.addProperty("type", normalizedType)
                        // Update description to match the new action type
                        val newDescription = when (normalizedType) {
                            "SKIP_NOTIFICATION" -> "Don't read this notification aloud"
                            else -> actionObject.get("description")?.asString ?: ""
                        }
                        actionObject.addProperty("description", newDescription)
                        normalized.copy(element = actionObject, wasMigrated = true, typeName = normalizedType)
                    } else {
                        normalized
                    }
                    if (finalElement.wasMigrated) {
                        actions.set(indexInRule, finalElement.element)
                        ruleMigrated = true
                        legacyFound = true
                    }
                    val type = finalElement.typeName
                    if (type == null || !knownActions.contains(type)) {
                        ruleNeedsAttention = true
                    }
                }
            }

            if (ruleMigrated) {
                migratedRulesCount++
            }
            if (ruleNeedsAttention) {
                attentionRuleIndexes.add(index)
            }
        }

        var finalRulesHash = rulesHash
        if (migratedRulesCount > 0) {
            val updatedJson = jsonArray.toString()
            rulesPrefs.edit().putString(RULES_KEY, updatedJson).apply()
            finalRulesHash = updatedJson.hashCode()
            InAppLogger.logDebug(TAG, "Migrated $migratedRulesCount rule(s) from legacy entries")
        }

        val totalRules = jsonArray.size()
        attentionRuleIndexes.addAll(validateRules(context, jsonArray))
        val attentionRulesCount = attentionRuleIndexes.size

        storeSummary(
            migrationPrefs = migrationPrefs,
            totalRules = totalRules,
            migratedRules = migratedRulesCount,
            attentionRules = attentionRulesCount,
            legacyFound = legacyFound,
            rulesHash = finalRulesHash
        )
    }

    private data class NormalizedElement(
        val element: JsonElement,
        val typeName: String?,
        val wasMigrated: Boolean
    )

    private fun normalizeTypedElement(
        element: JsonElement,
        typeKeys: List<String>,
        idPrefix: String
    ): NormalizedElement? {
        return when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val existingType = typeKeys.firstNotNullOfOrNull { key ->
                    obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString
                }
                if (existingType == null) {
                    null
                } else if (!obj.has("type") || typeKeys.any { it != "type" && obj.has(it) }) {
                    val updated = obj.deepCopy()
                    updated.addProperty("type", existingType)
                    typeKeys.filter { it != "type" }.forEach { updated.remove(it) }
                    NormalizedElement(updated, existingType, true)
                } else {
                    NormalizedElement(obj, existingType, false)
                }
            }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val typeValue = element.asString
                val obj = JsonObject()
                obj.addProperty("id", generateLegacyId(idPrefix))
                obj.addProperty("type", typeValue)
                obj.addProperty("enabled", true)
                obj.add("data", JsonObject())
                obj.addProperty("description", "")
                NormalizedElement(obj, typeValue, true)
            }
            else -> null
        }
    }

    private fun normalizeLegacyActionType(typeName: String?): String? {
        if (typeName == null) return null
        return if (typeName.equals(LEGACY_ACTION_DISABLE_SPEAKTHAT, ignoreCase = true)) {
            "SKIP_NOTIFICATION"
        } else {
            typeName
        }
    }

    private fun containsLegacyMarkers(rawJson: String): Boolean {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || trimmed == "[]") return false
        return trimmed.contains("\"$LEGACY_ACTION_DISABLE_SPEAKTHAT\"") ||
            trimmed.contains("\"$LEGACY_FIELD_CONDITIONS\"") ||
            trimmed.contains("\"$LEGACY_FIELD_CONDITION_LOGIC\"")
    }

    private fun generateLegacyId(prefix: String): String {
        return "${prefix}_${System.currentTimeMillis()}_${(0..999).random()}"
    }

    fun getSummary(context: Context): RuleMigrationSummary? {
        val prefs = getMigrationPrefs(context)
        if (!prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return null
        }
        return RuleMigrationSummary(
            totalRules = prefs.getInt(KEY_TOTAL_RULES, 0),
            migratedRules = prefs.getInt(KEY_MIGRATED_COUNT, 0),
            attentionRules = prefs.getInt(KEY_ATTENTION_COUNT, 0),
            legacyFound = prefs.getBoolean(KEY_LEGACY_FOUND, false),
            dismissed = prefs.getBoolean(KEY_MIGRATION_DISMISSED, false)
        )
    }

    fun dismissBanner(context: Context) {
        getMigrationPrefs(context).edit()
            .putBoolean(KEY_MIGRATION_DISMISSED, true)
            .apply()
    }

    private fun getMigrationPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
    }

    private fun storeSummary(
        migrationPrefs: SharedPreferences,
        totalRules: Int,
        migratedRules: Int,
        attentionRules: Int,
        legacyFound: Boolean,
        rulesHash: Int
    ) {
        migrationPrefs.edit()
            .putBoolean(KEY_MIGRATION_DONE, true)
            .putInt(KEY_TOTAL_RULES, totalRules)
            .putInt(KEY_MIGRATED_COUNT, migratedRules)
            .putInt(KEY_ATTENTION_COUNT, attentionRules)
            .putBoolean(KEY_LEGACY_FOUND, legacyFound)
            .putInt(KEY_LAST_RULES_HASH, rulesHash)
            .apply()
    }

    private fun validateRules(context: Context, jsonArray: JsonArray): Set<Int> {
        val gson = GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .registerTypeAdapter(Rule::class.java, RuleTypeAdapter())
            .registerTypeAdapter(Trigger::class.java, TriggerTypeAdapter())
            .registerTypeAdapter(Action::class.java, ActionTypeAdapter())
            .registerTypeAdapter(com.micoyc.speakthat.rules.Exception::class.java, ExceptionTypeAdapter())
            .registerTypeAdapter(com.micoyc.speakthat.rules.Exception::class.java, ExceptionTypeAdapter())
            .create()

        return try {
            val type = object : TypeToken<List<Rule>>() {}.type
            val rules = gson.fromJson<List<Rule>>(jsonArray.toString(), type) ?: emptyList()
            val ruleManager = RuleManager(context)
            rules.mapIndexedNotNull { index, rule ->
                if (!ruleManager.validateRule(rule).isValid) index else null
            }.toSet()
        } catch (e: Exception) {
            InAppLogger.logError(TAG, "Validation failed during migration: ${e.message}")
            if (jsonArray.size() > 0) setOf(0) else emptySet()
        }
    }
}
