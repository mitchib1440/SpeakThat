package com.micoyc.speakthat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import com.micoyc.speakthat.databinding.ActivityAppPickerBinding
import java.util.concurrent.Executors

class AppPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPickerBinding
    private lateinit var sharedPreferences: SharedPreferences
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val allApps = mutableListOf<SelectableApp>()
    private val filteredApps = mutableListOf<SelectableApp>()
    private lateinit var adapter: AppPickerAdapter

    private var allowPrivate = false
    private var initialPrivateSet: Set<String> = emptySet()
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        applySavedTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityAppPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        allowPrivate = intent.getBooleanExtra(EXTRA_ALLOW_PRIVATE, false)
        initialPrivateSet = intent.getStringArrayListExtra(EXTRA_PRIVATE_PACKAGES)?.toSet() ?: emptySet()
        val initialSelection = intent.getStringArrayListExtra(EXTRA_SELECTED_PACKAGES) ?: arrayListOf()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.filter_manage_apps)

        val toolbar = binding.appPickerToolbar
        toolbar.title = title
        toolbar.setNavigationOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        adapter = AppPickerAdapter(filteredApps, allowPrivate, ::onSelectionChanged)
        binding.recyclerApps.layoutManager = LinearLayoutManager(this)
        binding.recyclerApps.adapter = adapter

        binding.fabSave.setOnClickListener { returnSelection() }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        setLoading(true)
        loadInstalledApps(initialSelection)
    }

    private fun applySavedTheme() {
        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun loadInstalledApps(initialSelection: List<String>) {
        executor.execute {
            val pm = packageManager
            val selectableApps = mutableListOf<SelectableApp>()
            val selectedSet = initialSelection.toSet()

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchablePackages = pm.queryIntentActivities(launcherIntent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()

            InAppLogger.log("AppPicker", "Visible installed apps: ${installedApps.size}, launchable: ${launchablePackages.size}")

            installedApps.forEach { appInfo ->
                try {
                    val label = pm.getApplicationLabel(appInfo)?.toString().orEmpty()
                    if (label.isEmpty()) return@forEach

                    val icon = try { pm.getApplicationIcon(appInfo.packageName) } catch (_: Exception) { null }

                    val canPostNotifications = canPostNotifications(pm, appInfo)
                    val isLaunchable = launchablePackages.contains(appInfo.packageName)
                    val hasIconOrCanNotify = icon != null || canPostNotifications

                    if (!hasIconOrCanNotify && !isLaunchable) return@forEach

                    val isSelected = selectedSet.contains(appInfo.packageName)
                    val isPrivate = initialPrivateSet.contains(appInfo.packageName)

                    selectableApps.add(
                        SelectableApp(
                            label = label,
                            packageName = appInfo.packageName,
                            icon = icon,
                            selected = isSelected,
                            isPrivate = isPrivate
                        )
                    )
                } catch (e: Exception) {
                    InAppLogger.logError("AppPicker", "Error loading app ${appInfo.packageName}: ${e.message}")
                }
            }

            selectableApps.sortBy { it.label.lowercase() }

            mainHandler.post {
                setLoading(false)
                allApps.clear()
                allApps.addAll(selectableApps)
                InAppLogger.log("AppPicker", "Loaded ${allApps.size} apps into picker")
                applyFilter(binding.searchInput.text?.toString().orEmpty())
            }
        }
    }

    private fun canPostNotifications(pm: PackageManager, appInfo: ApplicationInfo): Boolean {
        val targetPre33 = appInfo.targetSdkVersion < 33
        var requestsPermission = false
        try {
            val pkgInfo: PackageInfo = pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
            val permissions = pkgInfo.requestedPermissions
            if (permissions != null) {
                for (permission in permissions) {
                    if ("android.permission.POST_NOTIFICATIONS" == permission) {
                        requestsPermission = true
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore and fall back
        }
        return targetPre33 || requestsPermission
    }

    private fun applyFilter(query: String) {
        val lower = query.lowercase().trim()
        val filtered = if (lower.isEmpty()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.lowercase().contains(lower) || app.packageName.lowercase().contains(lower)
            }
        }
        InAppLogger.log("AppPicker", "Filter query='$query' matches=${filtered.size}")
        filteredApps.clear()
        filteredApps.addAll(filtered)
        adapter.updateData(filteredApps)
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.progressLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.recyclerApps.visibility = if (loading) View.INVISIBLE else View.VISIBLE
    }

    private fun onSelectionChanged() {
        // No-op hook for now (reserved for future summaries)
    }

    private fun returnSelection() {
        val selectedPackages = ArrayList<String>()
        val privatePackages = ArrayList<String>()

        allApps.forEach { app ->
            if (app.selected) {
                selectedPackages.add(app.packageName)
                if (allowPrivate && app.isPrivate) {
                    privatePackages.add(app.packageName)
                }
            }
        }

        val data = Intent().apply {
            putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, selectedPackages)
            putStringArrayListExtra(EXTRA_PRIVATE_PACKAGES, privatePackages)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    data class SelectableApp(
        val label: String,
        val packageName: String,
        val icon: Drawable?,
        var selected: Boolean,
        var isPrivate: Boolean
    )

    companion object {
        private const val PREFS_NAME = "SpeakThatPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SELECTED_PACKAGES = "extra_selected_packages"
        const val EXTRA_PRIVATE_PACKAGES = "extra_private_packages"
        const val EXTRA_ALLOW_PRIVATE = "extra_allow_private"

        @JvmStatic
        fun createIntent(
            context: Context,
            title: String,
            selectedPackages: ArrayList<String>,
            privatePackages: ArrayList<String>,
            allowPrivate: Boolean
        ): Intent {
            return Intent(context, AppPickerActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putStringArrayListExtra(EXTRA_SELECTED_PACKAGES, selectedPackages)
                putStringArrayListExtra(EXTRA_PRIVATE_PACKAGES, privatePackages)
                putExtra(EXTRA_ALLOW_PRIVATE, allowPrivate)
            }
        }
    }
}

