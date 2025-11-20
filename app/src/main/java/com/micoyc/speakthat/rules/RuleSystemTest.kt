package com.micoyc.speakthat.rules

import android.content.Context
import com.micoyc.speakthat.InAppLogger
import com.micoyc.speakthat.automation.AutomationMode
import com.micoyc.speakthat.automation.AutomationModeManager

/**
 * Rule System Test
 * Tests the rule system with sample rules to verify functionality
 */

class RuleSystemTest(private val context: Context) {
    
    companion object {
        private const val TAG = "RuleSystemTest"
    }
    
    private val ruleManager = RuleManager(context)
    
    /**
     * Run comprehensive tests of the rule system
     */
    fun runTests() {
        InAppLogger.logDebug(TAG, "Starting rule system tests...")
        
        // Clear existing rules
        ruleManager.clearAllRules()
        
        // Test 1: Basic rule creation and validation
        testBasicRuleCreation()
        
        // Test 2: Screen state rule
        testScreenStateRule()
        
        // Test 3: Time schedule rule
        testTimeScheduleRule()
        
        // Test 4: WiFi network rule
        testWifiNetworkRule()
        
        // Test 5: Bluetooth device rule
        testBluetoothDeviceRule()
        
        // Test 6: Complex rule with multiple triggers and actions
        testComplexRule()
        
        // Test 7: Rule evaluation
        testRuleEvaluation()
        
        // Test 8: Natural language description
        testNaturalLanguageDescription()
        
        InAppLogger.logDebug(TAG, "Rule system tests completed")
    }
    
    /**
     * Run tests without creating pre-made rules (for user testing)
     */
    fun runTestsWithoutPreMadeRules() {
        InAppLogger.logDebug(TAG, "Starting rule system tests (no pre-made rules)...")
        
        // Save existing rules to restore them later
        val existingRules = ruleManager.getAllRules()
        InAppLogger.logDebug(TAG, "Saving ${existingRules.size} existing rules before tests")
        
        // Clear existing rules for clean test
        ruleManager.clearAllRules()
        
        // Test 7: Rule evaluation (with no rules)
        testRuleEvaluation()
        
        // Test 8: Blocking logic fix verification
        testBlockingLogicFix()
        
        // Restore existing rules
        InAppLogger.logDebug(TAG, "Restoring ${existingRules.size} existing rules after tests")
        existingRules.forEach { rule ->
            ruleManager.addRule(rule)
        }
        
        InAppLogger.logDebug(TAG, "Rule system tests completed (no pre-made rules)")
    }
    private fun testBasicRuleCreation() {
        InAppLogger.logDebug(TAG, "Test 1: Basic rule creation")
        
        val basicRule = Rule(
            name = "Test Basic Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "on"),
                    description = "Screen is on"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val validation = ruleManager.validateRule(basicRule)
        val success = ruleManager.addRule(basicRule)
        
        InAppLogger.logDebug(TAG, "Basic rule validation: ${validation.isValid}, Add success: $success")
        InAppLogger.logDebug(TAG, "Basic rule errors: ${validation.errors}")
    }
    
    private fun testScreenStateRule() {
        InAppLogger.logDebug(TAG, "Test 2: Screen state rule")
        
        val screenRule = Rule(
            name = "Screen Off Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "off"),
                    description = "Screen is off"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat when screen is off"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success = ruleManager.addRule(screenRule)
        InAppLogger.logDebug(TAG, "Screen state rule added: $success")
    }
    
    private fun testTimeScheduleRule() {
        InAppLogger.logDebug(TAG, "Test 3: Time schedule rule")
        
        val calendar = java.util.Calendar.getInstance()
        val currentTime = calendar.timeInMillis
        
        // Create a rule that's active for the next hour
        val startTime = currentTime
        val endTime = currentTime + (60 * 60 * 1000) // 1 hour from now
        
        val timeRule = Rule(
            name = "Time Schedule Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.TIME_SCHEDULE,
                    data = mapOf(
                        "start_time" to startTime,
                        "end_time" to endTime,
                        "days_of_week" to setOf(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK))
                    ),
                    description = "Within time schedule"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.CHANGE_VOICE_SETTINGS,
                    data = mapOf(
                        "voice_settings" to mapOf(
                            "speech_rate" to 0.8f,
                            "pitch" to 1.2f
                        )
                    ),
                    description = "Change voice settings"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success = ruleManager.addRule(timeRule)
        InAppLogger.logDebug(TAG, "Time schedule rule added: $success")
    }
    
    private fun testWifiNetworkRule() {
        InAppLogger.logDebug(TAG, "Test 4: WiFi network rule")
        
        val wifiRule = Rule(
            name = "Home WiFi Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.WIFI_NETWORK,
                    data = mapOf(
                        "network_ssids" to setOf("HomeWiFi", "MyNetwork")
                    ),
                    description = "Connected to home WiFi"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.ENABLE_APP_FILTER,
                    data = mapOf("app_package" to "com.whatsapp"),
                    description = "Enable WhatsApp filter"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success = ruleManager.addRule(wifiRule)
        InAppLogger.logDebug(TAG, "WiFi network rule added: $success")
    }
    
    private fun testBluetoothDeviceRule() {
        InAppLogger.logDebug(TAG, "Test 5: Bluetooth device rule")
        
        val bluetoothRule = Rule(
            name = "Car Bluetooth Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.BLUETOOTH_DEVICE,
                    data = mapOf(
                        "device_addresses" to setOf("00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF")
                    ),
                    description = "Connected to car Bluetooth"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat in car"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success = ruleManager.addRule(bluetoothRule)
        InAppLogger.logDebug(TAG, "Bluetooth device rule added: $success")
    }
    
    private fun testComplexRule() {
        InAppLogger.logDebug(TAG, "Test 6: Complex rule with multiple triggers and actions")
        
        val complexRule = Rule(
            name = "Night Mode Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.TIME_SCHEDULE,
                    data = mapOf(
                        "start_time" to 22 * 60 * 60 * 1000L, // 22:00
                        "end_time" to 7 * 60 * 60 * 1000L,   // 07:00
                        "days_of_week" to setOf(
                            java.util.Calendar.MONDAY,
                            java.util.Calendar.TUESDAY,
                            java.util.Calendar.WEDNESDAY,
                            java.util.Calendar.THURSDAY,
                            java.util.Calendar.FRIDAY
                        )
                    ),
                    description = "Weekday nights"
                ),
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "on"),
                    description = "Screen is on"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat during night mode"
                ),
                Action(
                    type = ActionType.CHANGE_VOICE_SETTINGS,
                    data = mapOf(
                        "voice_settings" to mapOf(
                            "speech_rate" to 0.7f,
                            "pitch" to 0.9f
                        )
                    ),
                    description = "Reduce voice volume at night"
                )
            ),
            exceptions = listOf(
                Exception(
                    type = ExceptionType.WIFI_NETWORK,
                    data = mapOf(
                        "network_ssids" to setOf("EmergencyWiFi")
                    ),
                    description = "Except when on emergency WiFi"
                )
            ),
            triggerLogic = LogicGate.AND,
            exceptionLogic = LogicGate.OR
        )
        
        val success = ruleManager.addRule(complexRule)
        InAppLogger.logDebug(TAG, "Complex rule added: $success")
    }
    
    private fun testRuleEvaluation() {
        InAppLogger.logDebug(TAG, "Test 7: Rule evaluation")
        
        // Enable the rules system
        AutomationModeManager(context).setMode(AutomationMode.CONDITIONAL_RULES)
        
        // Get all rules
        val allRules = ruleManager.getAllRules()
        InAppLogger.logDebug(TAG, "Total rules: ${allRules.size}")
        
        // Evaluate all rules
        val evaluationResults = ruleManager.evaluateAllRules()
        InAppLogger.logDebug(TAG, "Evaluation results: ${evaluationResults.size}")
        
        evaluationResults.forEach { result ->
            InAppLogger.logDebug(TAG, result.getLogMessage())
        }
        
        // Check if any rules should block notifications
        val shouldBlock = ruleManager.shouldBlockNotification()
        val blockingRules = ruleManager.getBlockingRuleNames()
        
        InAppLogger.logDebug(TAG, "Should block notifications: $shouldBlock")
        InAppLogger.logDebug(TAG, "Blocking rules: $blockingRules")
        
        // Get rule statistics
        val stats = ruleManager.getRuleStats()
        InAppLogger.logDebug(TAG, "Rule stats: $stats")
    }
    
    /**
     * Create a simple test rule for quick testing
     */
    fun createSimpleTestRule(): Boolean {
        val testRule = Rule(
            name = "Simple Test Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "on"),
                    description = "Screen is on"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat"
                )
            )
        )
        
        return ruleManager.addRule(testRule)
    }
    
    /**
     * Test the fix for the blocking logic bug
     * This test verifies that only rules with DISABLE_SPEAKTHAT actions block notifications
     */
    fun testBlockingLogicFix() {
        InAppLogger.logDebug(TAG, "Test 8: Blocking logic fix verification")
        
        // Save existing rules to restore them later
        val existingRules = ruleManager.getAllRules()
        InAppLogger.logDebug(TAG, "Saving ${existingRules.size} existing rules before test")
        
        // Clear existing rules for clean test
        ruleManager.clearAllRules()
        
        // Enable the rules system
        AutomationModeManager(context).setMode(AutomationMode.CONDITIONAL_RULES)
        
        // Test 1: Rule with DISABLE_SPEAKTHAT action should block
        val blockingRule = Rule(
            name = "Blocking Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.WIFI_NETWORK,
                    data = mapOf("network_ssids" to setOf<String>()), // Any network
                    description = "Connected to any WiFi"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat"
                )
            )
        )
        ruleManager.addRule(blockingRule)
        
        // Test 2: Rule with CHANGE_VOICE_SETTINGS action should NOT block
        val nonBlockingRule = Rule(
            name = "Non-Blocking Rule",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.WIFI_NETWORK,
                    data = mapOf("network_ssids" to setOf<String>()), // Any network
                    description = "Connected to any WiFi"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.CHANGE_VOICE_SETTINGS,
                    data = mapOf(
                        "voice_settings" to mapOf(
                            "speech_rate" to 1.5f,
                            "pitch" to 0.5f
                        )
                    ),
                    description = "Change voice settings"
                )
            )
        )
        ruleManager.addRule(nonBlockingRule)
        
        // Evaluate the rules
        val shouldBlock = ruleManager.shouldBlockNotification()
        val blockingRules = ruleManager.getBlockingRuleNames()
        
        InAppLogger.logDebug(TAG, "Blocking logic test results:")
        InAppLogger.logDebug(TAG, "- Should block notifications: $shouldBlock")
        InAppLogger.logDebug(TAG, "- Blocking rules: ${blockingRules.joinToString(", ")}")
        
        // Verify the fix works correctly
        val expectedBlocking = shouldBlock && blockingRules.contains("Blocking Rule") && !blockingRules.contains("Non-Blocking Rule")
        InAppLogger.logDebug(TAG, "Blocking logic fix verification: ${if (expectedBlocking) "PASSED" else "FAILED"}")
        
        // Clean up test rules and restore existing rules
        ruleManager.clearAllRules()
        InAppLogger.logDebug(TAG, "Restoring ${existingRules.size} existing rules after test")
        existingRules.forEach { rule ->
            ruleManager.addRule(rule)
        }
    }
    
    /**
     * Test the inversion functionality for triggers and exceptions
     * This test demonstrates how inverted conditions work
     */
    fun testInversionFunctionality(): Boolean {
        InAppLogger.logDebug(TAG, "Test: Inversion functionality")
        
        // Test 1: Inverted Bluetooth trigger (disable when NO headphones connected)
        val invertedBluetoothRule = Rule(
            name = "Disable when no headphones",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.BLUETOOTH_DEVICE,
                    inverted = true, // NEW: Invert the condition
                    data = mapOf("device_addresses" to setOf("00:11:22:33:44:55")), // Headphone MAC
                    description = "No headphones connected"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat when no headphones"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success1 = ruleManager.addRule(invertedBluetoothRule)
        InAppLogger.logDebug(TAG, "Inverted Bluetooth rule added: $success1")
        
        // Test 2: Inverted screen state trigger (disable when screen is ON)
        val invertedScreenRule = Rule(
            name = "Disable when screen on",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    inverted = true, // NEW: Invert the condition
                    data = mapOf("screen_state" to "on"),
                    description = "Screen is ON (inverted)"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat when screen is on"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        val success2 = ruleManager.addRule(invertedScreenRule)
        InAppLogger.logDebug(TAG, "Inverted screen state rule added: $success2")
        
        // Test 3: Rule with inverted exception (allow when NOT on home WiFi)
        val invertedExceptionRule = Rule(
            name = "Allow when not on home WiFi",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.TIME_SCHEDULE,
                    data = mapOf(
                        "start_time" to 0L,
                        "end_time" to 23 * 60 * 60 * 1000L, // 23:00
                        "days_of_week" to setOf(1, 2, 3, 4, 5) // Weekdays
                    ),
                    description = "During work hours"
                )
            ),
            exceptions = listOf(
                Exception(
                    type = ExceptionType.WIFI_NETWORK,
                    inverted = true, // NEW: Invert the exception
                    data = mapOf("network_ssids" to setOf("HomeWiFi")),
                    description = "NOT on home WiFi"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Disable SpeakThat during work hours when not on home WiFi"
                )
            ),
            triggerLogic = LogicGate.AND,
            exceptionLogic = LogicGate.AND
        )
        
        val success3 = ruleManager.addRule(invertedExceptionRule)
        InAppLogger.logDebug(TAG, "Inverted exception rule added: $success3")
        
        return success1 && success2 && success3
    }
    
    /**
     * Test the natural language description functionality
     */
    fun testNaturalLanguageDescription() {
        InAppLogger.logDebug(TAG, "Test 8: Natural language description")
        
        // Create a simple rule to test natural language description
        val simpleRule = Rule(
            name = "Quiet Hours",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "on"),
                    description = "Screen is on"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading notifications"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        // Test the natural language description
        val naturalDescription = simpleRule.getNaturalLanguageDescription(context)
        InAppLogger.logDebug(TAG, "Natural language description: $naturalDescription")
        
        // Create a rule with exceptions
        val ruleWithException = Rule(
            name = "Work Hours",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.TIME_SCHEDULE,
                    data = mapOf(
                        "start_time" to 9 * 60 * 60 * 1000L, // 09:00
                        "end_time" to 17 * 60 * 60 * 1000L,  // 17:00
                        "days_of_week" to setOf(1, 2, 3, 4, 5) // Weekdays
                    ),
                    description = "Between 9 AM and 5 PM"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading notifications"
                )
            ),
            exceptions = listOf(
                Exception(
                    type = ExceptionType.WIFI_NETWORK,
                    data = mapOf("network_ssids" to setOf("Home WiFi")),
                    description = "Connected to Home WiFi"
                )
            ),
            triggerLogic = LogicGate.AND,
            exceptionLogic = LogicGate.AND
        )
        
        // Test the natural language description with exception
        val naturalDescriptionWithException = ruleWithException.getNaturalLanguageDescription(context)
        InAppLogger.logDebug(TAG, "Natural language description with exception: $naturalDescriptionWithException")
        
        // Create a rule with multiple triggers
        val ruleWithMultipleTriggers = Rule(
            name = "Home Mode",
            enabled = true,
            triggers = listOf(
                Trigger(
                    type = TriggerType.WIFI_NETWORK,
                    data = mapOf("network_ssids" to setOf("Home WiFi")),
                    description = "Connected to Home WiFi"
                ),
                Trigger(
                    type = TriggerType.SCREEN_STATE,
                    data = mapOf("screen_state" to "off"),
                    description = "Screen is off"
                )
            ),
            actions = listOf(
                Action(
                    type = ActionType.DISABLE_SPEAKTHAT,
                    description = "Skip reading notifications"
                )
            ),
            triggerLogic = LogicGate.AND
        )
        
        // Test the natural language description with multiple triggers
        val naturalDescriptionMultipleTriggers = ruleWithMultipleTriggers.getNaturalLanguageDescription(context)
        InAppLogger.logDebug(TAG, "Natural language description with multiple triggers: $naturalDescriptionMultipleTriggers")
        
        InAppLogger.logDebug(TAG, "Natural language description test completed")
    }
    
    /**
     * Get test results summary
     */
    fun getTestSummary(): String {
        val stats = ruleManager.getRuleStats()
        val shouldBlock = ruleManager.shouldBlockNotification()
        val blockingRules = ruleManager.getBlockingRuleNames()
        
        return """
            Rule System Test Summary:
            - Total rules: ${stats.totalRules}
            - Enabled rules: ${stats.enabledRules}
            - Rules with triggers: ${stats.rulesWithTriggers}
            - Rules with actions: ${stats.rulesWithActions}
            - Rules with exceptions: ${stats.rulesWithExceptions}
            - Should block notifications: $shouldBlock
            - Blocking rules: ${blockingRules.joinToString(", ")}
        """.trimIndent()
    }
} 