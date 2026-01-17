package com.micoyc.speakthat.rules.migration

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
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

    fun runIfNeeded(context: Context) {
        val rulesPrefs = context.getSharedPreferences(RULES_PREFS, Context.MODE_PRIVATE)
        val migrationPrefs = getMigrationPrefs(context)

        val rulesJson = rulesPrefs.getString(RULES_KEY, "[]") ?: "[]"
        val rulesHash = rulesJson.hashCode()
        val migrationDone = migrationPrefs.getBoolean(KEY_MIGRATION_DONE, false)
        val lastRulesHash = migrationPrefs.getInt(KEY_LAST_RULES_HASH, 0)

        if (migrationDone && lastRulesHash == rulesHash) {
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

        var legacyFound = false
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

            val triggersElement = ruleObject.get("triggers")
            val triggers = if (triggersElement != null && triggersElement.isJsonArray) {
                triggersElement.asJsonArray
            } else {
                null
            }
            if (triggers == null) {
                ruleNeedsAttention = true
            } else {
                for (trigger in triggers) {
                    if (!trigger.isJsonObject) {
                        ruleNeedsAttention = true
                        continue
                    }
                    val type = trigger.asJsonObject.get("type")?.asString
                    if (type == null || !knownTriggers.contains(type)) {
                        ruleNeedsAttention = true
                    }
                }
            }

            val exceptionsElement = ruleObject.get("exceptions")
            val exceptions = if (exceptionsElement != null && exceptionsElement.isJsonArray) {
                exceptionsElement.asJsonArray
            } else {
                null
            }
            if (exceptions != null) {
                for (exception in exceptions) {
                    if (!exception.isJsonObject) {
                        ruleNeedsAttention = true
                        continue
                    }
                    val type = exception.asJsonObject.get("type")?.asString
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
                for (action in actions) {
                    if (!action.isJsonObject) {
                        ruleNeedsAttention = true
                        continue
                    }
                    val actionObject = action.asJsonObject
                    val type = actionObject.get("type")?.asString
                    if (type == null) {
                        ruleNeedsAttention = true
                        continue
                    }
                    if (type == "DISABLE_SPEAKTHAT") {
                        actionObject.addProperty("type", "SKIP_NOTIFICATION")
                        legacyFound = true
                        ruleMigrated = true
                    } else if (!knownActions.contains(type)) {
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

        if (migratedRulesCount > 0) {
            rulesPrefs.edit().putString(RULES_KEY, jsonArray.toString()).apply()
            InAppLogger.logDebug(TAG, "Migrated $migratedRulesCount rule(s) from legacy actions")
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
            rulesHash = rulesHash
        )
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
