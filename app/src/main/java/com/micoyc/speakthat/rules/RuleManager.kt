package com.micoyc.speakthat.rules

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.rules.ActionExecutor
import com.micoyc.speakthat.rules.ActionType

/**
 * Rule Manager
 * Handles rule storage, retrieval, and integration with the notification system
 */

class RuleManager(private val context: Context) {
    
    companion object {
        private const val TAG = "RuleManager"
        private const val PREFS_NAME = "SpeakThatRules"
        private const val KEY_RULES_ENABLED = "rules_enabled"
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
    private val actionExecutor = ActionExecutor(context)
    
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
            InAppLogger.logDebug(TAG, "Loaded ${rules.size} rules from storage")
            rules
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
    fun evaluateAllRules(): List<RuleEvaluationResult> {
        if (!isRulesEnabled()) {
            InAppLogger.logDebug(TAG, "Rules system is disabled, skipping evaluation")
            return emptyList()
        }
        
        val currentTime = System.currentTimeMillis()
        
        // Check if we have time-sensitive rules that require more frequent evaluation
        val enabledRules = getEnabledRules()
        val hasTimeSensitiveRules = enabledRules.any { rule ->
            rule.triggers.any { trigger ->
                trigger.enabled && trigger.type == TriggerType.TIME_SCHEDULE
            }
        }
        
        // Use shorter cache duration for time-sensitive rules
        val effectiveCacheDuration = if (hasTimeSensitiveRules) {
            50L // 50ms for time-sensitive rules
        } else {
            evaluationCacheDuration // 100ms for other rules
        }
        
        // Return cached evaluation results if they're still valid
        if (lastEvaluationResults != null && (currentTime - lastEvaluationTime) < effectiveCacheDuration) {
            InAppLogger.logDebug(TAG, "Using cached evaluation results (${lastEvaluationResults!!.size} rules) - cache age: ${currentTime - lastEvaluationTime}ms, cache duration: ${effectiveCacheDuration}ms")
            return lastEvaluationResults!!
        }
        
        // Cache is stale or null, perform fresh evaluation
        InAppLogger.logDebug(TAG, "Evaluating ${enabledRules.size} enabled rules")
        
        val results = enabledRules.map { rule ->
            ruleEvaluator.evaluateRule(rule)
        }
        
        // Cache the results
        lastEvaluationResults = results
        lastEvaluationTime = currentTime
        
        return results
    }
    
    /**
     * Check if any rules should execute (for notification filtering)
     */
    fun shouldBlockNotification(): Boolean {
        if (!isRulesEnabled()) {
            return false
        }
        
        val evaluationResults = evaluateAllRules()
        val executingRules = evaluationResults.filter { it.shouldExecute }
        
        if (executingRules.isNotEmpty()) {
            InAppLogger.logDebug(TAG, "Found ${executingRules.size} executing rules: ${executingRules.map { it.ruleName }}")
            
            // Execute actions for all rules that should execute
            executeActionsForRules(executingRules)
            
            // Check if any of the executing rules contain DISABLE_SPEAKTHAT actions
            val shouldBlock = executingRules.any { ruleResult ->
                val rule = getRule(ruleResult.ruleId)
                val hasDisableAction = rule?.actions?.any { action ->
                    action.enabled && action.type == ActionType.DISABLE_SPEAKTHAT
                } ?: false
                
                InAppLogger.logDebug(TAG, "Rule '${ruleResult.ruleName}' has DISABLE_SPEAKTHAT action: $hasDisableAction")
                hasDisableAction
            }
            
            InAppLogger.logDebug(TAG, "Should block notifications: $shouldBlock")
            
            if (shouldBlock) {
                val blockingRules = executingRules.filter { ruleResult ->
                    val rule = getRule(ruleResult.ruleId)
                    rule?.actions?.any { action ->
                        action.enabled && action.type == ActionType.DISABLE_SPEAKTHAT
                    } ?: false
                }
                InAppLogger.logDebug(TAG, "Rules blocking notification: ${blockingRules.map { it.ruleName }}")
            }
            
            return shouldBlock
        }
        
        return false
    }
    
    /**
     * Execute actions for rules that should execute
     */
    private fun executeActionsForRules(rules: List<RuleEvaluationResult>) {
        val allActions = mutableListOf<Action>()
        
        rules.forEach { ruleResult ->
            val rule = getRule(ruleResult.ruleId)
            rule?.let { 
                allActions.addAll(it.actions)
            }
        }
        
        if (allActions.isNotEmpty()) {
            InAppLogger.logDebug(TAG, "Executing ${allActions.size} actions for ${rules.size} rules")
            val results = actionExecutor.executeActions(allActions)
            
            results.forEach { result ->
                InAppLogger.logDebug(TAG, result.getLogMessage())
            }
        }
    }
    
    /**
     * Get the names of rules that are currently blocking notifications
     */
    fun getBlockingRuleNames(): List<String> {
        if (!isRulesEnabled()) {
            return emptyList()
        }
        
        return evaluateAllRules()
            .filter { it.shouldExecute }
            .filter { ruleResult ->
                val rule = getRule(ruleResult.ruleId)
                rule?.actions?.any { action ->
                    action.enabled && action.type == ActionType.DISABLE_SPEAKTHAT
                } ?: false
            }
            .map { it.ruleName }
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
            errors.add("Rule name cannot be empty")
        }
        
        // Check triggers
        if (rule.triggers.isEmpty()) {
            errors.add("Rule must have at least one trigger")
        } else {
            val enabledTriggers = rule.triggers.filter { it.enabled }
            if (enabledTriggers.isEmpty()) {
                errors.add("Rule must have at least one enabled trigger")
            }
        }
        
        // Check actions
        if (rule.actions.isEmpty()) {
            errors.add("Rule must have at least one action")
        } else {
            val enabledActions = rule.actions.filter { it.enabled }
            if (enabledActions.isEmpty()) {
                errors.add("Rule must have at least one enabled action")
            }
        }
        
        // Check for duplicate rule names
        val existingRules = loadRules().filter { it.id != rule.id }
        if (existingRules.any { it.name.equals(rule.name, ignoreCase = true) }) {
            errors.add("A rule with this name already exists")
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