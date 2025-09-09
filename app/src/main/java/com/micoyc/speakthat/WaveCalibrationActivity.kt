package com.micoyc.speakthat

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.micoyc.speakthat.databinding.ActivityWaveCalibrationBinding
import kotlinx.coroutines.*
import java.util.*
import com.bumptech.glide.Glide

class WaveCalibrationActivity : AppCompatActivity(), SensorEventListener {
    
    companion object {
        private const val TAG = "WaveCalibration"
        private const val CALIBRATION_DURATION_MS = 8000L // 8 seconds
        private const val SENSOR_UPDATE_INTERVAL_MS = 50L // 50ms intervals
        private const val THRESHOLD_PERCENTAGE = 0.6f // 60% of max distance
        private const val MIN_READINGS_REQUIRED = 5 // Reduced for testing - minimum data points needed
        
        // SharedPreferences keys
        private const val CALIBRATION_DATA_KEY = "wave_calibration_data_v1"
        private const val SENSOR_MAX_RANGE_KEY = "sensor_max_range_v1"
        private const val WAVE_THRESHOLD_KEY = "wave_threshold_v1"
        private const val CALIBRATION_TIMESTAMP_KEY = "calibration_timestamp_v1"
    }
    
    private lateinit var binding: ActivityWaveCalibrationBinding
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    
    private var isCalibrating = false
    private var isTesting = false
    private val readings = mutableListOf<Float>()
    private val handler = Handler(Looper.getMainLooper())
    private var calibrationJob: Job? = null
    
    private var calculatedThreshold = 0f
    private var maxDistance = 0f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "WaveCalibrationActivity onCreate started")
        
        // Initialize logging
        InAppLogger.initialize(this)
        InAppLogger.log("WaveCalibration", "Wave calibration activity started")
        
        // Apply saved theme FIRST before anything else
        val mainPrefs = getSharedPreferences("SpeakThatPrefs", android.content.Context.MODE_PRIVATE)
        applySavedTheme(mainPrefs)
        
        try {
            binding = ActivityWaveCalibrationBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "WaveCalibrationActivity layout inflated successfully")

            // Load the calibration demo GIF
            Glide.with(this)
                .asGif()
                .load(R.drawable.calibration_demo)
                .into(binding.imgCalibrationDemo)
            
            setupSensor()
            setupUI()
            Log.d(TAG, "WaveCalibrationActivity onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "WaveCalibrationActivity onCreate failed", e)
            InAppLogger.logError("WaveCalibration", "Activity creation failed: ${e.message}")
            Toast.makeText(this, "Failed to initialize calibration: ${e.message}", Toast.LENGTH_LONG).show()
            InAppLogger.logUserAction("Wave calibration initialization failed - user feedback shown")
            finish()
        }
    }

    private fun applySavedTheme(prefs: android.content.SharedPreferences) {
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        
        if (isDarkMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
    
    private fun setupSensor() {
        Log.d(TAG, "Setting up proximity sensor")
        InAppLogger.log("WaveCalibration", "Setting up proximity sensor")
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        if (proximitySensor == null) {
            Log.e(TAG, "No proximity sensor found on this device")
            InAppLogger.logError("WaveCalibration", "No proximity sensor found on this device")
            showError("No proximity sensor found on this device")
            return
        }
        
        Log.d(TAG, "Proximity sensor found: ${proximitySensor?.name}")
        Log.d(TAG, "Max range: ${proximitySensor?.maximumRange}cm")
        Log.d(TAG, "Sensor setup completed successfully")
        
        InAppLogger.log("WaveCalibration", "Proximity sensor found: ${proximitySensor?.name}, max range: ${proximitySensor?.maximumRange}cm")
    }
    
    private fun setupUI() {
        binding.btnStartCalibration.setOnClickListener {
            InAppLogger.logUserAction("Wave calibration start button clicked")
            startCalibration()
        }
        
        binding.btnRecalibrate.setOnClickListener {
            InAppLogger.logUserAction("Wave calibration recalibrate button clicked")
            startCalibration()
        }
        
        binding.btnConfirm.setOnClickListener {
            InAppLogger.logUserAction("Wave calibration confirm button clicked")
            saveCalibrationAndFinish()
        }
        
        binding.btnDisable.setOnClickListener {
            InAppLogger.logUserAction("Wave calibration disable button clicked")
            disableWaveToStop()
        }
        
        // Show initial state
        showCalibrationReady()
    }
    
    private fun startCalibration() {
        Log.d(TAG, "startCalibration called")
        InAppLogger.log("WaveCalibration", "Starting wave calibration process")
        
        // Cancel any existing calibration job
        calibrationJob?.cancel()
        
        // Ensure sensor listener is unregistered before re-registering
        try {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Previous sensor listener unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering previous sensor listener", e)
        }
        
        if (proximitySensor == null) {
            Log.e(TAG, "Proximity sensor not available for calibration")
            InAppLogger.logError("WaveCalibration", "Proximity sensor not available for calibration")
            showError("Proximity sensor not available")
            return
        }
        
        readings.clear()
        isCalibrating = true
        
        // Register sensor listener with retry mechanism
        var success = false
        var attempts = 0
        val maxAttempts = 3
        
        while (!success && attempts < maxAttempts) {
            attempts++
            Log.d(TAG, "Attempting to register sensor listener (attempt $attempts)")
            
            success = sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
            
            if (!success && attempts < maxAttempts) {
                Log.w(TAG, "Sensor registration failed, retrying in 500ms...")
                Thread.sleep(500)
            }
        }
        
        if (success) {
            Log.d(TAG, "Sensor listener registered successfully for calibration")
            InAppLogger.log("WaveCalibration", "Sensor listener registered successfully for calibration")
        } else {
            Log.e(TAG, "Failed to register sensor listener after $maxAttempts attempts")
            InAppLogger.logError("WaveCalibration", "Failed to register sensor listener after $maxAttempts attempts")
            showError("Failed to register proximity sensor listener after multiple attempts")
            return
        }
        
        // Update UI
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Wave your hand over the sensor continuously for 8 seconds...\n\n" +
            "📊 Live Data:\n" +
            "• Readings collected: 0/20+\n" +
            "• Current reading: --\n" +
            "• Max distance: --\n" +
            "• Min distance: --\n" +
            "• Different values: 0"
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))
        binding.btnStartCalibration.visibility = View.GONE
        binding.btnRecalibrate.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.btnDisable.visibility = View.GONE
        
        // Start calibration timer
        calibrationJob = CoroutineScope(Dispatchers.Main).launch {
            delay(CALIBRATION_DURATION_MS)
            finishCalibration()
        }
        
        // Also add a safety check after 2 seconds to see if we're getting any readings
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            if (readings.isEmpty()) {
                Log.w(TAG, "No sensor readings received after 2 seconds - sensor may not be working")
                handler.post {
                    binding.tvStatus.text = "⚠️ No sensor readings detected!\n\n" +
                        "Please check:\n" +
                        "• Proximity sensor is not covered\n" +
                        "• Wave your hand over the sensor\n" +
                        "• Try covering the sensor completely"
                }
            }
        }
        
        Log.d(TAG, "Calibration started - recording for ${CALIBRATION_DURATION_MS}ms")
    }
    
    private fun finishCalibration() {
        Log.d(TAG, "finishCalibration called - readings count: ${readings.size}")
        InAppLogger.log("WaveCalibration", "Calibration finished - readings count: ${readings.size}")
        isCalibrating = false
        
        // Properly unregister sensor listener
        try {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Sensor listener unregistered in finishCalibration")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering sensor listener in finishCalibration", e)
        }
        
        // Calculate threshold
        if (readings.isNotEmpty()) {
            maxDistance = readings.maxOrNull() ?: 0f
            calculatedThreshold = maxDistance * THRESHOLD_PERCENTAGE
            
            Log.d(TAG, "Calibration complete - Max: ${maxDistance}cm, Threshold: ${calculatedThreshold}cm")
            Log.d(TAG, "Readings summary - Min: ${readings.minOrNull()}cm, Max: ${maxDistance}cm, Distinct: ${readings.distinct().size}")
            
            InAppLogger.log("WaveCalibration", "Calibration complete - Max: ${maxDistance}cm, Threshold: ${calculatedThreshold}cm, Distinct values: ${readings.distinct().size}")
            
            val validationError = validateCalibrationData()
            if (validationError == null) {
                Log.d(TAG, "Calibration validation passed - showing test mode")
                InAppLogger.log("WaveCalibration", "Calibration validation passed - showing test mode")
                showTestMode()
            } else {
                Log.w(TAG, "Calibration validation failed: $validationError")
                InAppLogger.logError("WaveCalibration", "Calibration validation failed: $validationError")
                showCalibrationFailed(validationError)
            }
        } else {
            val errorMessage = "No sensor readings collected during calibration. Please ensure your proximity sensor is working and try again."
            Log.e(TAG, errorMessage)
            InAppLogger.logError("WaveCalibration", "No sensor readings collected during calibration")
            showCalibrationFailed(errorMessage)
        }
    }
    
    private fun validateCalibrationData(): String? {
        val minReading = readings.minOrNull() ?: 0f
        val distinctReadings = readings.distinct().size
        
        Log.d(TAG, "Validation - Min: ${minReading}cm, Max: ${maxDistance}cm, Distinct: $distinctReadings, Total: ${readings.size}")
        
        return when {
            readings.size < MIN_READINGS_REQUIRED -> {
                val message = "Not enough data collected. Please wave your hand more during calibration. (Got ${readings.size} readings, need at least $MIN_READINGS_REQUIRED)"
                Log.w(TAG, message)
                message
            }
            maxDistance < 1f -> {
                val message = "Sensor readings too low. Maximum distance detected was ${String.format("%.1f", maxDistance)}cm. Please ensure your proximity sensor is working properly."
                Log.w(TAG, message)
                message
            }
            maxDistance == minReading -> {
                val message = "No variation detected. All readings were ${String.format("%.1f", maxDistance)}cm. Please wave your hand over the sensor to create different readings."
                Log.w(TAG, message)
                message
            }
            distinctReadings < 2 -> {
                val message = "Insufficient sensor variation. Only $distinctReadings different values detected. Please wave your hand more to create varied readings."
                Log.w(TAG, message)
                message
            }
            else -> null // Success
        }
    }
    
    private fun showTestMode() {
        isTesting = true

        // Hide the how-it-works section
        binding.imgCalibrationDemo.parent?.let {
            (it as? View)?.visibility = View.GONE
        }
        binding.imgCalibrationDemo.visibility = View.GONE
        // If you have a LinearLayout for the whole section, hide it:
        // binding.howItWorksSection.visibility = View.GONE

        // Register sensor for live testing
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)

        // Update UI
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.text = "Test your wave detection"
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        binding.tvThreshold.text = "Threshold: ${String.format("%.1f", calculatedThreshold)}cm (${(THRESHOLD_PERCENTAGE * 100).toInt()}% of ${String.format("%.1f", maxDistance)}cm)"
        binding.tvThreshold.visibility = View.VISIBLE
        binding.btnRecalibrate.visibility = View.VISIBLE
        binding.btnConfirm.visibility = View.VISIBLE
        binding.btnDisable.visibility = View.VISIBLE

        Log.d(TAG, "Entering test mode with threshold: ${calculatedThreshold}cm")
    }
    
    private fun showCalibrationReady() {
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.text = "Ready to calibrate wave detection"
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_blue_dark))
        binding.tvThreshold.visibility = View.GONE
        binding.btnStartCalibration.visibility = View.VISIBLE
        binding.btnRecalibrate.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.btnDisable.visibility = View.GONE
    }
    
    private fun showCalibrationFailed(errorMessage: String? = null) {
        isTesting = false

        // Hide the how-it-works section
        binding.imgCalibrationDemo.parent?.let {
            (it as? View)?.visibility = View.GONE
        }
        binding.imgCalibrationDemo.visibility = View.GONE
        // If you have a LinearLayout for the whole section, hide it:
        // binding.howItWorksSection.visibility = View.GONE

        // Properly unregister sensor listener
        try {
            sensorManager.unregisterListener(this)
            Log.d(TAG, "Sensor listener unregistered in showCalibrationFailed")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering sensor listener in showCalibrationFailed", e)
        }

        binding.progressBar.visibility = View.GONE

        val statusText = if (errorMessage != null) {
            "Calibration failed\n\n$errorMessage"
        } else {
            "Calibration failed - please try again"
        }

        binding.tvStatus.text = statusText
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        binding.tvThreshold.visibility = View.GONE
        binding.btnStartCalibration.visibility = View.VISIBLE
        binding.btnRecalibrate.visibility = View.GONE
        binding.btnConfirm.visibility = View.GONE
        binding.btnDisable.visibility = View.VISIBLE

        Log.w(TAG, "Calibration failed: $errorMessage")
        InAppLogger.logError("WaveCalibration", "Calibration failed: $errorMessage")
    }
    
    private fun saveCalibrationAndFinish() {
        Log.d(TAG, "saveCalibrationAndFinish called")
        InAppLogger.log("WaveCalibration", "Saving calibration data and finishing")
        
        val sharedPreferences = getSharedPreferences("BehaviorSettings", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        
        // Save calibration data (only max distance and timestamp)
        editor.putFloat(SENSOR_MAX_RANGE_KEY, maxDistance)
        editor.putString(CALIBRATION_DATA_KEY, readings.joinToString(","))
        editor.putLong(CALIBRATION_TIMESTAMP_KEY, System.currentTimeMillis())
        editor.putBoolean("wave_to_stop_enabled", true)
        editor.apply()
        
        Log.d(TAG, "Calibration saved - Max: ${maxDistance}cm")
        Log.d(TAG, "About to set result RESULT_OK and finish activity")
        
        InAppLogger.log("WaveCalibration", "Calibration saved successfully - Max: ${maxDistance}cm, Threshold: ${calculatedThreshold}cm")
        
        Toast.makeText(this, "Wave detection calibrated successfully!", Toast.LENGTH_SHORT).show()
        InAppLogger.logUserAction("Wave calibration completed successfully - user feedback shown")
        setResult(RESULT_OK)
        
        // Add a small delay to ensure the result is set before finishing
        handler.postDelayed({
            Log.d(TAG, "Finishing WaveCalibrationActivity")
            finish()
        }, 100)
    }
    
    private fun disableWaveToStop() {
        val sharedPreferences = getSharedPreferences("BehaviorSettings", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("wave_to_stop_enabled", false)
        editor.apply()
        
        Log.d(TAG, "Wave-to-stop disabled by user")
        
        Toast.makeText(this, "Wave-to-stop disabled", Toast.LENGTH_SHORT).show()
        InAppLogger.logUserAction("Wave-to-stop disabled - user feedback shown")
        setResult(RESULT_CANCELED)
        finish()
    }
    
    private fun showError(message: String) {
        binding.tvStatus.text = message
        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
        binding.btnStartCalibration.visibility = View.GONE
        binding.btnDisable.visibility = View.VISIBLE
        
        Log.e(TAG, "Error: $message")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        Log.d(TAG, "Sensor event received: type=${event.sensor.type}, value=${event.values[0]}, isCalibrating=$isCalibrating, isTesting=$isTesting")
        
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val proximityValue = event.values[0]
            
            if (isCalibrating) {
                // Record readings during calibration
                readings.add(proximityValue)
                
                // Update live display on every reading for immediate feedback
                val currentMax = readings.maxOrNull() ?: 0f
                val currentMin = readings.minOrNull() ?: 0f
                val distinctCount = readings.distinct().size
                
                handler.post {
                    binding.tvStatus.text = "Wave your hand over the sensor continuously...\n\n" +
                        "📊 Live Data:\n" +
                        "• Readings collected: ${readings.size}/20+\n" +
                        "• Current reading: ${String.format("%.1f", proximityValue)}cm\n" +
                        "• Max distance: ${String.format("%.1f", currentMax)}cm\n" +
                        "• Min distance: ${String.format("%.1f", currentMin)}cm\n" +
                        "• Different values: $distinctCount"
                }
                
                Log.d(TAG, "Calibration reading #${readings.size}: ${proximityValue}cm (max: ${currentMax}cm)")
                
                // Log every 10th reading to InAppLogger to avoid spam
                if (readings.size % 10 == 0) {
                    InAppLogger.log("WaveCalibration", "Calibration progress: ${readings.size} readings, current: ${proximityValue}cm, max: ${currentMax}cm, distinct: $distinctCount")
                }
            } else if (isTesting) {
                // Show live feedback during testing
                val isWaveDetected = if (proximityValue == 0f) {
                    true
                } else {
                    proximityValue < calculatedThreshold
                }
                
                handler.post {
                    if (isWaveDetected) {
                        binding.tvStatus.text = "🟢 Wave detected: TRUE\n🔇 Notifications will be SILENCED"
                        binding.tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                    } else {
                        binding.tvStatus.text = "🔴 Wave detected: FALSE\n🔊 Notifications will be READ"
                        binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    }
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for proximity sensor
    }
    
    override fun onDestroy() {
        super.onDestroy()
        InAppLogger.log("WaveCalibration", "Wave calibration activity destroyed")
        calibrationJob?.cancel()
        sensorManager.unregisterListener(this)
    }
} 