package com.micoyc.speakthat.rules

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.BuildConfig
import com.micoyc.speakthat.InAppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumSet
import java.util.Locale

object RuleConfigManager {

    private const val TAG = "RuleConfig"
    private const val CONFIG_VERSION = "1.0"
    private const val EXPORT_TYPE_RULES = "SpeakThat_RulesConfig"

    enum class RulePermissionType {
        BLUETOOTH,
        WIFI
    }

    data class RuleImportResult(
        val success: Boolean,
        val message: String,
        val importedCount: Int,
        val skippedCount: Int
    )

    @JvmStatic
    fun exportRules(context: Context): String {
        val ruleManager = RuleManager(context)
        val rules = ruleManager.getAllRules()
        val gson = buildGson()

        val metadata = JSONObject()
            .put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("configVersion", CONFIG_VERSION)
            .put("exportType", EXPORT_TYPE_RULES)

        val json = JSONObject()
            .put("metadata", metadata)
            .put("rules", JSONArray(gson.toJson(rules)))
            .put("rulesCount", rules.size)

        return json.toString(2)
    }

    @JvmStatic
    fun extractRulesFromRulesConfig(jsonData: String): List<Rule> {
        val json = JSONObject(jsonData)
        val metadata = json.optJSONObject("metadata")
            ?: throw IllegalArgumentException("Missing metadata section")
        val exportType = metadata.optString("exportType", "")
        if (exportType != EXPORT_TYPE_RULES) {
            throw IllegalArgumentException("Invalid rules configuration type")
        }
        return extractRulesFromJson(json) ?: emptyList()
    }

    @JvmStatic
    fun extractRulesFromFullConfig(jsonData: String): List<Rule> {
        val json = JSONObject(jsonData)
        return extractRulesFromJson(json) ?: emptyList()
    }

    @JvmStatic
    fun importRules(context: Context, rules: List<Rule>, skippedCount: Int): RuleImportResult {
        return try {
            val ruleManager = RuleManager(context)
            ruleManager.saveRules(rules)
            InAppLogger.log(TAG, "Imported ${rules.size} rules (skipped $skippedCount)")
            RuleImportResult(
                success = true,
                message = "",
                importedCount = rules.size,
                skippedCount = skippedCount
            )
        } catch (e: kotlin.Exception) {
            InAppLogger.logError(TAG, "Rules import failed: ${e.message}")
            RuleImportResult(
                success = false,
                message = e.message ?: "",
                importedCount = 0,
                skippedCount = skippedCount
            )
        }
    }

    @JvmStatic
    fun getRequiredPermissionTypes(rules: List<Rule>): EnumSet<RulePermissionType> {
        val required = EnumSet.noneOf(RulePermissionType::class.java)
        if (rules.any { ruleRequiresBluetooth(it) }) {
            required.add(RulePermissionType.BLUETOOTH)
        }
        if (rules.any { ruleRequiresWifi(it) }) {
            required.add(RulePermissionType.WIFI)
        }
        return required
    }

    @JvmStatic
    fun filterRulesByPermissions(
        rules: List<Rule>,
        allowBluetooth: Boolean,
        allowWifi: Boolean
    ): List<Rule> {
        return rules.filter { rule ->
            val needsBluetooth = ruleRequiresBluetooth(rule)
            val needsWifi = ruleRequiresWifi(rule)
            (!needsBluetooth || allowBluetooth) && (!needsWifi || allowWifi)
        }
    }

    private fun extractRulesFromJson(json: JSONObject): List<Rule>? {
        if (!json.has("rules")) {
            return null
        }
        val rulesArray = json.optJSONArray("rules") ?: JSONArray()
        val rulesJson = rulesArray.toString()
        val type = object : TypeToken<List<Rule>>() {}.type
        return buildGson().fromJson(rulesJson, type) ?: emptyList()
    }

    private fun ruleRequiresBluetooth(rule: Rule): Boolean {
        val triggerRequires = rule.triggers.any { it.enabled && it.type == TriggerType.BLUETOOTH_DEVICE }
        val exceptionRequires = rule.exceptions.any { it.enabled && it.type == ExceptionType.BLUETOOTH_DEVICE }
        return triggerRequires || exceptionRequires
    }

    private fun ruleRequiresWifi(rule: Rule): Boolean {
        val triggerRequires = rule.triggers.any { it.enabled && it.type == TriggerType.WIFI_NETWORK }
        val exceptionRequires = rule.exceptions.any { it.enabled && it.type == ExceptionType.WIFI_NETWORK }
        return triggerRequires || exceptionRequires
    }

    private fun buildGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
            .registerTypeAdapter(Rule::class.java, RuleTypeAdapter())
            .registerTypeAdapter(Trigger::class.java, TriggerTypeAdapter())
            .registerTypeAdapter(Action::class.java, ActionTypeAdapter())
            .registerTypeAdapter(com.micoyc.speakthat.rules.Exception::class.java, ExceptionTypeAdapter())
            .create()
    }
}
