package com.hdvideoplay.media

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.nav.AppNav
import com.hdvideoplay.media.ui.recall.RecallPopupController
import com.hdvideoplay.media.ui.theme.MediaTheme

/**
 * Business shell only. Cold/hot splash and App Open are owned by SplashActivity
 * and HotStartManager; MainActivity is always entered after the splash finishes.
 */
class MainActivity : ComponentActivity() {
    private var homePermissionRequested = false
    private var returningFromNotificationSettings = false
    private val permissionPrefs by lazy {
        getSharedPreferences("notification_permission_state", MODE_PRIVATE)
    }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionPrefs.edit().putInt(KEY_HOME_DENIALS, 0).apply()
            } else {
                val denials = permissionPrefs.getInt(KEY_HOME_DENIALS, 0) + 1
                permissionPrefs.edit().putInt(KEY_HOME_DENIALS, denials).apply()
                if (denials >= 2 && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    showNotificationPermissionDialog()
                }
            }
        }
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            returningFromNotificationSettings = false
            if (hasNotificationPermission()) permissionPrefs.edit().putInt(KEY_HOME_DENIALS, 0).apply()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaTheme {
                AppNav()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requestHomeNotificationPermissionOnce()
        // Cold/hot app-start events only; route changes do not trigger the popup.
        RecallPopupController.onAppForeground(this)
        // Preload pools for the next return; load is not show.
        AdScheduler.load(AdScenes.HOT_OPEN)
        AdScheduler.load(AdScenes.NORMAL_1)
        AdScheduler.load(AdScenes.NORMAL_2)
    }

    private fun requestHomeNotificationPermissionOnce() {
        if (homePermissionRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasNotificationPermission()
        ) return
        homePermissionRequested = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun showNotificationPermissionDialog() {
        if (isFinishing || isDestroyed || returningFromNotificationSettings) return
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.notification_permission_allow) { _, _ ->
                returningFromNotificationSettings = true
                settingsLauncher.launch(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    ),
                )
            }
            .setNegativeButton(R.string.notification_permission_deny, null)
            .show()
    }

    companion object {
        private const val KEY_HOME_DENIALS = "home_denials"
    }
}
