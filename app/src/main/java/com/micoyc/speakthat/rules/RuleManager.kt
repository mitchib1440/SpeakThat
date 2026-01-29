package com.micoyc.speakthat.rules

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.InAppLogger

/**
 * Rule Manager
 * Handles rule storage, retrieval, and integration with the notification system
 */

class RuleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RuleManager"
        private const val PREFS_NAME = "SpeakThatRules"
        private const val KEY_RULES_LIST = "rules_list"
        private const val KEY_MASTER_TOGGLE = "master_toggle"
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = GsonBuilder()
        .registerTypeAdapter(object : TypeToken<Map<String, Any>>() {}.type, MapStringAnyTypeAdapter())
        .registerTypeAdapter(Rule::class.java, RuleTypeAdapter())
        .registerTypeAdapter(Trigger::class.java, TriggerTypeAdapter())
        .registerTypeAdapter(Action::class.java, ActionTypeAdapter())
        .registerTypeAdapter(Exception::class.java, ExceptionTypeAdapter())
        .create()
    private val ruleEvaluator = RuleEvaluator(context)
    
    // ============================================================================
    // CACHING SYSTEM
    // ============================================================================
    
    @Volatile
    private var cachedRules: List<Rule>? = null
    private var lastCacheTime: Long = 0L
    private val cacheValidityDuration = 5000L // 5 seconds cache validity
    
    // Evaluation result caching for single notification processing
    private var lastEvaluationResults: List<RuleEvaluationResult>? = null
    private var lastEvaluationTime: Long = 0L
    private val evaluationCacheDuration = 100L // Reduced to 100ms for time-sensitive rules
    private var lastEvaluationContextHash: Int = 0
    
    /**
     * Get rules with caching - only loads from storage if cache is stale
     */
    private fun getRulesWithCache(): List<Rule> {
        val currentTime = System.currentTimeMillis()
        
        // Return cached rules if they're still valid
        if (cachedRules != null && (currentTime - lastCacheTime) < cacheValidityDuration) {
            return cachedRules!!
        }
        
        // Cache is stale or null, reload from storage
        val rules = loadRulesFromStorage()
        cachedRules = rules
        lastCacheTime = currentTime
        
        InAppLogger.logDebug(TAG, "Cache refreshed - loaded ${rules.size} rules from storage")
        return rules
    }
    
    /**
     * Invalidate the cache - forces next call to reload from storage
     */
    private fun invalidateCache() {
        cachedRules = null
        lastCacheTime = 0L
        lastEvaluationResults = null // Also invalidate evaluation cache
        lastEvaluationTime = 0L
        InAppLogger.logDebug(TAG, "Cache invalidated")
    }
    
    /**
     * Load rules directly from storage (bypasses cache)
     */
    private fun loadRulesFromStorage(): List<Rule> {
        val rulesJson = sharedPreferences.getString(KEY_RULES_LIST, "[]")
        // Only log raw JSON in verbose mode to reduce noise
        if (InAppLogger.verboseMode) {
            InAppLogger.logDebug(TAG, "Raw JSON loaded: $rulesJson")
        }
        return try {
            val type = object : TypeToken<List<Rule>>() {}.type
            val rules = gson.fromJson(rulesJson, type) ?: emptyList<Rule>()
            val migratedRules = migrateLegacyActions(rules)
            InAppLogger.logDebug(TAG, "Loaded ${migratedRules.size} rules from storage")
            migratedRules
        } catch (e: Throwable) {
            InAppLogger.logError(TAG, "Error loading rules: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Preload the cache - useful for initialization
     */
    fun preloadCache() {
        getRulesWithCache()
        InAppLogger.logDebug(TAG, "Cache preloaded")
    }
    
    /**
     * Invalidate only the evaluation cache (keeps rule cache)
     * Useful when conditions might have changed but rules haven't
     */
    fun invalidateEvaluationCache() {
        lastEvaluationResults = null
        lastEvaluationTime = 0L
        lastEvaluationContextHash = 0
        InAppLogger.logDebug(TAG, "Evaluation cache invalidated")
    }
    
    // ============================================================================
    // MASTER TOGGLE
    // ============================================================================
    
    /**
     * Check if the rules system is enabled globally
     */
    fun isRulesEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_MASTER_TOGGLE, false)
    }
    
    /**
     * Enable or disable the rules system globally
     */
    fun setRulesEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_MASTER_TOGGLE, enabled).apply()
        InAppLogger.logDebug(TAG, "Rules system ${if (enabled) "enabled" else "disabled"}")
    }
    
    // ============================================================================
    // RULE STORAGE
    // ============================================================================
    
    /**
     * Save a list of rules to persistent storage
     */
    fun saveRules(rules: List<Rule>) {
        val rulesJson = gson.toJson(rules)
        sharedPreferences.edit().putString(KEY_RULES_LIST, rulesJson).apply()
        invalidateCache() // Invalidate cache after saving
        InAppLogger.logDebug(TAG, "Saved ${rules.size} rules to storage")
        InAppLogger.logDebug(TAG, "Raw JSON saved: $rulesJson")
    }
    
    /**
     * Load all rules from persistent storage (with caching)
     */
    fun loadRules(): List<Rule> {
        return getRulesWithCache()
    }
    
    /**
     * Add a new rule
     */
    fun addRule(rule: Rule): Boolean {
        val currentRules = loadRules().toMutableList()
        currentRules.add(rule)
        saveRules(currentRules)
        InAppLogger.logDebug(TAG, "Added rule: ${rule.getLogMessage()}")
        return true
    }
    
    /**
     * Update an existing rule
     */
    fun updateRule(updatedRule: Rule): Boolean {
        val currentRules = loadRules().toMutableList()
        InAppLogger.logDebug(TAG, "Attempting to update rule with ID: ${updatedRule.id}")
        InAppLogger.logDebug(TAG, "Current rules in storage: ${currentRules.map { "${it.name}(${it.id})" }}")
        
        val index = currentRules.indexOfFirst { it.id == updatedRule.id }
        
        if (index != -1) {
            currentRules[index] = updatedRule.copy(modifiedAt = System.currentTimeMillis())
            saveRules(currentRules)
            InAppLogger.logDebug(TAG, "Updated rule: ${updatedRule.getLogMessage()}")
            return true
        } else {
            InAppLogger.logError(TAG, "Rule not found for update: ${updatedRule.id}")
            InAppLogger.logError(TAG, "Available rule IDs: ${currentRules.map { it.id }}")
            return false
        }
    }
    
    /**
     * Delete a rule by ID
     */
    fun deleteRule(ruleId: String): Boolean {
        val currentRules = loadRules().toMutableList()
        val removed = currentRules.removeAll { it.id == ruleId }
        
        if (removed) {
            saveRules(currentRules)
            InAppLogger.logDebug(TAG, "Deleted rule: $ruleId")
            return true
        } else {
            InAppLogger.logError(TAG, "Rule not found for deletion: $ruleId")
            return false
        }
    }
    
    /**
     * Get a rule by ID
     */
    fun getRule(ruleId: String): Rule? {
        return loadRules().find { it.id == ruleId }
    }
    
    /**
     * Get all enabled rules
     */
    fun getEnabledRules(): List<Rule> {
        return loadRules().filter { it.enabled }
    }
    
    /**
     * Get all rules (enabled and disabled)
     */
    fun getAllRules(): List<Rule> {
        return loadRules()
    }
    
    // ============================================================================
    // RULE EVALUATION
    // ============================================================================
    
    /**
     * Evaluate all enabled rules and return the results (with caching)
     */
    fun evaluateAllRules(notificationContext: NotificationContext): List<RuleEvaluationResult> {
        if (!isRulesEnabled()) {
            InAppLogger.logDebug(TAG, "Rules system is disabled, skipping evaluation")
            return emptyList()
        }
        
        val currentTime = System.currentTimeMillis()
        val contextHash = computeContextHash(notificationContext)
        
        // Check if we have time-sensitive or non-cacheable rules
        val enabledRules = getEnabledRules()
        val hasTimeSensitiveRules = enabledRules.any { rule ->
            rule.triggers.any { trigger ->
                trigger.enabled && trigger.type == TriggerType.TIME_SCHEDULE
            }
        }
        val hasNonCacheableRules = enabledRules.any { rule ->
            rule.triggers.any { trigger -> trigger.enabled && !trigger.type.isCacheable } ||
                rule.exceptions.any { exception -> exception.enabled && !exception.type.isCacheable }
        }
        
        // Use shorter cache duration for time-sensitive rules
        val effectiveCacheDuration = if (hasTimeSensitiveRules) {
            50L // 50ms for time-sensitive rules
        } else {
            evaluationCacheDuration // 100ms for other rules
        }
        
        // Return cached evaluation results if they're still valid
        val shouldUseCache = !hasNonCacheableRules
        if (shouldUseCache &&
            lastEvaluationResults != null &&
            (currentTime - lastEvaluationTime) < effectiveCacheDuration &&
            lastEvaluationContextHash == contextHash
        ) {
            InAppLogger.logDebug(TAG, "Using cached evaluation results (${lastEvaluationResults!!.size} rules) - cache age: ${currentTime - lastEvaluationTime}ms, cache duration: ${effectiveCacheDuration}ms")
            return lastEvaluationResults!!
        }

        if (!shouldUseCache) {
            InAppLogger.logDebug(TAG, "Skipping evaluation cache due to non-cacheable rule types")
        }
        
        // Cache is stale or null, perform fresh evaluation
        InAppLogger.logDebug(TAG, "Evaluating ${enabledRules.size} enabled rules")
        
        val results = enabledRules.map { rule ->
            ruleEvaluator.evaluateRule(rule, notificationContext)
        }
        
        // Cache the results
        lastEvaluationResults = results
        lastEvaluationTime = currentTime
        lastEvaluationContextHash = contextHash
        
        return results
    }
    
    /**
     * Check if any rules should execute (for notification filtering)
     */
    fun evaluateNotification(notificationContext: NotificationContext): EvaluationOutcome {
        if (!isRulesEnabled()) {
            return EvaluationOutcome(emptyList(), emptyList())
        }

        val evaluationResults = evaluateAllRules(notificationContext)
        val executingRules = evaluationResults.filter { it.shouldExecute }

        if (executingRules.isEmpty()) {
            return EvaluationOutcome(emptyList(), emptyList())
        }

        val matchedRuleNames = mutableListOf<String>()
        val rawEffects = mutableListOf<Effect>()

        executingRules.forEach { ruleResult ->
            val rule = getRule(ruleResult.ruleId)
            rule?.let { safeRule ->
                matchedRuleNames.add(safeRule.name)
                rawEffects.addAll(mapActionsToEffects(safeRule.actions))
            }
        }

        val aggregatedEffects = aggregateEffects(rawEffects)
        InAppLogger.logDebug(TAG, "Evaluation outcome: effects=${aggregatedEffects.map { it::class.simpleName }}, matchedRules=$matchedRuleNames")

        return EvaluationOutcome(aggregatedEffects, matchedRuleNames)
    }
    
    /**
     * Execute actions for rules that should execute
     */
    private fun mapActionsToEffects(actions: List<Action>): List<Effect> {
        return actions.filter { it.enabled }.mapNotNull { action ->
            when (action.type) {
                ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> {
                    val template = action.data["template"] as? String ?: ""
                    val templateKey = action.data["template_key"] as? String
                    if (template.isBlank() && templateKey.isNullOrBlank()) {
                        null
                    } else {
                        Effect.SetSpeechTemplate(template, templateKey)
                    }
                }
                ActionType.FORCE_PRIVATE -> Effect.ForcePrivate
                ActionType.OVERRIDE_PRIVATE -> Effect.OverridePrivate
                ActionType.SKIP_NOTIFICATION -> Effect.SkipNotification
                ActionType.DISABLE_SPEAKTHAT -> Effect.SkipNotification
                ActionType.SET_MASTER_SWITCH -> {
                    val enabled = action.data["enabled"] as? Boolean ?: false
                    Effect.SetMasterSwitch(enabled)
                }
            }
        }
    }

    private fun aggregateEffects(effects: List<Effect>): List<Effect> {
        val aggregated = mutableListOf<Effect>()

        val masterSwitch = effects.filterIsInstance<Effect.SetMasterSwitch>().lastOrNull()
        if (masterSwitch != null) {
            aggregated.add(masterSwitch)
        }

        if (effects.any { it is Effect.SkipNotification }) {
            aggregated.add(Effect.SkipNotification)
            return aggregated
        }

        val forcePrivate = effects.any { it is Effect.ForcePrivate }
        val overridePrivate = effects.any { it is Effect.OverridePrivate }
        when {
            forcePrivate -> aggregated.add(Effect.ForcePrivate)
            overridePrivate -> aggregated.add(Effect.OverridePrivate)
        }

        val speechTemplate = effects.filterIsInstance<Effect.SetSpeechTemplate>().lastOrNull()
        if (speechTemplate != null) {
            aggregated.add(speechTemplate)
        }

        val mediaBehavior = effects.filterIsInstance<Effect.SetMediaBehavior>().lastOrNull()
        if (mediaBehavior != null) {
            aggregated.add(mediaBehavior)
        }

        val gestureUpdates = effects.filterIsInstance<Effect.SetGestureEnabled>()
        if (gestureUpdates.isNotEmpty()) {
            val lastByGesture = gestureUpdates.associateBy { it.gesture }.values
            aggregated.addAll(lastByGesture)
        }

        return aggregated
    }
    
    /**
     * Get the names of rules that are currently blocking notifications
     */
    fun getBlockingRuleNames(notificationContext: NotificationContext): List<String> {
        if (!isRulesEnabled()) {
            return emptyList()
        }

        val outcome = evaluateNotification(notificationContext)
        val isBlocking = outcome.effects.any { it is Effect.SkipNotification }
        return if (isBlocking) outcome.matchedRules else emptyList()
    }
    
    // ============================================================================
    // RULE VALIDATION
    // ============================================================================
    
    /**
     * Validate a rule before saving
     */
    fun validateRule(rule: Rule): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check rule name
        if (rule.name.isBlank()) {
            errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_name_empty))
        }
        
        // Check triggers
        if (rule.triggers.isEmpty()) {
            errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_no_triggers))
        } else {
            val enabledTriggers = rule.triggers.filter { it.enabled }
            if (enabledTriggers.isEmpty()) {
                errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_no_enabled_triggers))
            }
        }
        
        // Check actions
        if (rule.actions.isEmpty()) {
            errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_no_actions))
        } else {
            val enabledActions = rule.actions.filter { it.enabled }
            if (enabledActions.isEmpty()) {
                errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_no_enabled_actions))
            }
        }
        
        // Check for duplicate rule names
        val existingRules = loadRules().filter { it.id != rule.id }
        if (existingRules.any { it.name.equals(rule.name, ignoreCase = true) }) {
            errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_duplicate_name))
        }

        // Validate trigger data
        rule.triggers.filter { it.enabled }.forEach { trigger ->
            errors.addAll(validateTriggerData(trigger))
        }

        // Validate exception data
        rule.exceptions.filter { it.enabled }.forEach { exception ->
            errors.addAll(validateExceptionData(exception))
        }

        // Validate action data
        rule.actions.filter { it.enabled }.forEach { action ->
            errors.addAll(validateActionData(action))
        }
        
        val isValid = errors.isEmpty()
        InAppLogger.logDebug(TAG, "Rule validation: ${if (isValid) "PASSED" else "FAILED"} - ${errors.joinToString(", ")}")
        
        return ValidationResult(isValid, errors)
    }
    
    // ============================================================================
    // UTILITY METHODS
    // ============================================================================
    
    /**
     * Clear all rules (for testing/reset)
     */
    fun clearAllRules() {
        saveRules(emptyList())
        InAppLogger.logDebug(TAG, "All rules cleared")
    }
    
    /**
     * Get rule statistics
     */
    fun getRuleStats(): RuleStats {
        val allRules = loadRules()
        val enabledRules = allRules.filter { it.enabled }
        
        return RuleStats(
            totalRules = allRules.size,
            enabledRules = enabledRules.size,
            disabledRules = allRules.size - enabledRules.size,
            rulesWithTriggers = allRules.count { it.triggers.isNotEmpty() },
            rulesWithActions = allRules.count { it.actions.isNotEmpty() },
            rulesWithExceptions = allRules.count { it.exceptions.isNotEmpty() }
        )
    }

    private fun migrateLegacyActions(rules: List<Rule>): List<Rule> {
        var migrated = false

        val updatedRules = rules.map { rule ->
            val updatedActions = rule.actions.map { action ->
                if (action.type == ActionType.DISABLE_SPEAKTHAT) {
                    migrated = true
                    action.copy(type = ActionType.SKIP_NOTIFICATION)
                } else {
                    action
                }
            }

            if (updatedActions != rule.actions) {
                rule.copy(actions = updatedActions)
            } else {
                rule
            }
        }

        if (migrated) {
            InAppLogger.logDebug(TAG, "Migrating legacy DISABLE_SPEAKTHAT actions to SKIP_NOTIFICATION")
            val rulesJson = gson.toJson(updatedRules)
            sharedPreferences.edit().putString(KEY_RULES_LIST, rulesJson).apply()
            invalidateCache()
        }

        return updatedRules
    }

    private fun validateTriggerData(trigger: Trigger): List<String> {
        val errors = mutableListOf<String>()
        when (trigger.type) {
            TriggerType.BLUETOOTH_DEVICE -> {
                if (!isStringSetOrList(trigger.data["device_addresses"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_bluetooth_devices))
                }
            }
            TriggerType.BATTERY_PERCENTAGE -> {
                val mode = trigger.data["mode"] as? String
                val percentage = trigger.data["percentage"]
                val percentValue = when (percentage) {
                    is Int -> percentage
                    is Long -> percentage.toInt()
                    is Double -> percentage.toInt()
                    is Float -> percentage.toInt()
                    is Number -> percentage.toInt()
                    is String -> percentage.toIntOrNull()
                    else -> null
                }
                if (mode !in setOf("above", "below")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_battery_mode))
                }
                if (percentValue == null || percentValue !in 0..100) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_battery_percentage))
                }
            }
            TriggerType.CHARGING_STATUS -> {
                val status = trigger.data["status"] as? String
                if (status !in setOf("charging", "discharging")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_charging_status))
                }
            }
            TriggerType.NOTIFICATION_CONTAINS -> {
                val phrase = trigger.data["phrase"] as? String
                val caseSensitive = trigger.data["case_sensitive"]
                if (phrase.isNullOrBlank()) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_notification_phrase))
                }
                if (caseSensitive != null && caseSensitive !is Boolean) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_case_sensitive))
                }
            }
            TriggerType.NOTIFICATION_FROM -> {
                val packagesData = trigger.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                if (packages.isEmpty() || !isStringSetOrList(packagesData)) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_notification_from_apps))
                }
            }
            TriggerType.FOREGROUND_APP -> {
                val packagesData = trigger.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                if (packages.isEmpty() || !isStringSetOrList(packagesData)) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_foreground_apps))
                }
            }
            TriggerType.DEVICE_UNLOCKED -> {
                val mode = trigger.data["mode"] as? String
                if (mode !in setOf("locked", "unlocked")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_unlock_mode))
                }
            }
            TriggerType.SCREEN_ORIENTATION -> {
                val mode = trigger.data["mode"] as? String
                if (mode !in setOf("portrait", "landscape")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_orientation_mode))
                }
            }
            TriggerType.SCREEN_STATE -> {
                val state = trigger.data["screen_state"] as? String
                if (state != null && state.lowercase() !in setOf("on", "off")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_screen_state))
                }
            }
            TriggerType.TIME_SCHEDULE -> {
                val startTime = trigger.data["start_time"]
                val endTime = trigger.data["end_time"]
                if (startTime !is Number || endTime !is Number) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_time_schedule))
                }
                if (!isDaySet(trigger.data["days_of_week"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_days_of_week))
                }
            }
            TriggerType.WIFI_NETWORK -> {
                if (!isStringSetOrList(trigger.data["network_ssids"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_wifi_networks))
                }
            }
            TriggerType.WIRED_HEADPHONES -> {
                val connectionState = trigger.data["connection_state"] as? String
                if (connectionState !in setOf("connected", "disconnected")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_wired_headphones_state))
                }
            }
        }
        return errors
    }

    private fun validateExceptionData(exception: Exception): List<String> {
        val errors = mutableListOf<String>()
        when (exception.type) {
            ExceptionType.BLUETOOTH_DEVICE -> {
                if (!isStringSetOrList(exception.data["device_addresses"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_bluetooth_devices))
                }
            }
            ExceptionType.BATTERY_PERCENTAGE -> {
                val mode = exception.data["mode"] as? String
                val percentage = exception.data["percentage"]
                val percentValue = when (percentage) {
                    is Int -> percentage
                    is Long -> percentage.toInt()
                    is Double -> percentage.toInt()
                    is Float -> percentage.toInt()
                    is Number -> percentage.toInt()
                    is String -> percentage.toIntOrNull()
                    else -> null
                }
                if (mode !in setOf("above", "below")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_battery_mode))
                }
                if (percentValue == null || percentValue !in 0..100) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_battery_percentage))
                }
            }
            ExceptionType.CHARGING_STATUS -> {
                val status = exception.data["status"] as? String
                if (status !in setOf("charging", "discharging")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_charging_status))
                }
            }
            ExceptionType.NOTIFICATION_CONTAINS -> {
                val phrase = exception.data["phrase"] as? String
                val caseSensitive = exception.data["case_sensitive"]
                if (phrase.isNullOrBlank()) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_notification_phrase))
                }
                if (caseSensitive != null && caseSensitive !is Boolean) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_case_sensitive))
                }
            }
            ExceptionType.NOTIFICATION_FROM -> {
                val packagesData = exception.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                if (packages.isEmpty() || !isStringSetOrList(packagesData)) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_notification_from_apps))
                }
            }
            ExceptionType.FOREGROUND_APP -> {
                val packagesData = exception.data["app_packages"]
                val packages = when (packagesData) {
                    is Set<*> -> packagesData.filterIsInstance<String>()
                    is List<*> -> packagesData.filterIsInstance<String>()
                    else -> emptyList()
                }
                if (packages.isEmpty() || !isStringSetOrList(packagesData)) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_foreground_apps))
                }
            }
            ExceptionType.DEVICE_UNLOCKED -> {
                val mode = exception.data["mode"] as? String
                if (mode !in setOf("locked", "unlocked")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_unlock_mode))
                }
            }
            ExceptionType.SCREEN_ORIENTATION -> {
                val mode = exception.data["mode"] as? String
                if (mode !in setOf("portrait", "landscape")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_orientation_mode))
                }
            }
            ExceptionType.SCREEN_STATE -> {
                val state = exception.data["screen_state"] as? String
                if (state != null && state.lowercase() !in setOf("on", "off")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_screen_state))
                }
            }
            ExceptionType.TIME_SCHEDULE -> {
                val startTime = exception.data["start_time"]
                val endTime = exception.data["end_time"]
                if (startTime !is Number || endTime !is Number) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_time_schedule))
                }
                if (!isDaySet(exception.data["days_of_week"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_days_of_week))
                }
            }
            ExceptionType.WIFI_NETWORK -> {
                if (!isStringSetOrList(exception.data["network_ssids"])) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_wifi_networks))
                }
            }
            ExceptionType.WIRED_HEADPHONES -> {
                val connectionState = exception.data["connection_state"] as? String
                if (connectionState !in setOf("connected", "disconnected")) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_wired_headphones_state))
                }
            }
        }
        return errors
    }

    private fun validateActionData(action: Action): List<String> {
        val errors = mutableListOf<String>()
        when (action.type) {
            ActionType.APPLY_CUSTOM_SPEECH_FORMAT -> {
                val template = action.data["template"] as? String
                if (template.isNullOrBlank()) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_speech_template))
                }
            }
            ActionType.FORCE_PRIVATE -> {
                // No additional data required
            }
            ActionType.OVERRIDE_PRIVATE -> {
                // No additional data required
            }
            ActionType.SET_MASTER_SWITCH -> {
                if (action.data["enabled"] !is Boolean) {
                    errors.add(context.getString(com.micoyc.speakthat.R.string.rule_error_invalid_master_switch))
                }
            }
            ActionType.SKIP_NOTIFICATION,
            ActionType.DISABLE_SPEAKTHAT -> {
                // No data validation needed
            }
        }
        return errors
    }

    private fun isStringSetOrList(value: Any?): Boolean {
        if (value == null) return true
        return when (value) {
            is Set<*> -> value.all { it is String }
            is List<*> -> value.all { it is String }
            else -> false
        }
    }

    private fun isDaySet(value: Any?): Boolean {
        if (value == null) return true
        val numbers = when (value) {
            is Set<*> -> value
            is List<*> -> value
            is Array<*> -> value.toList()
            else -> return false
        }
        return numbers.all { item ->
            val day = when (item) {
                is Int -> item
                is Long -> item.toInt()
                is Double -> item.toInt()
                is Float -> item.toInt()
                is Number -> item.toInt()
                is String -> item.toIntOrNull()
                else -> null
            } ?: return@all false
            day in 0..6
        }
    }

    private fun computeContextHash(notificationContext: NotificationContext): Int {
        var result = notificationContext.packageName.hashCode()
        result = 31 * result + (notificationContext.title?.hashCode() ?: 0)
        result = 31 * result + (notificationContext.text?.hashCode() ?: 0)
        result = 31 * result + (notificationContext.bigText?.hashCode() ?: 0)
        result = 31 * result + (notificationContext.ticker?.hashCode() ?: 0)
        result = 31 * result + (notificationContext.category?.hashCode() ?: 0)
        result = 31 * result + (notificationContext.channelId?.hashCode() ?: 0)
        result = 31 * result + notificationContext.isOngoing.hashCode()
        result = 31 * result + notificationContext.postTime.hashCode()
        return result
    }
}

// ============================================================================
// SUPPORTING DATA CLASSES
// ============================================================================

/**
 * Result of rule validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Statistics about stored rules
 */
data class RuleStats(
    val totalRules: Int,
    val enabledRules: Int,
    val disabledRules: Int,
    val rulesWithTriggers: Int,
    val rulesWithActions: Int,
    val rulesWithExceptions: Int
) 