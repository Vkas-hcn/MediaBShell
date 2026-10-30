package com.hdvideoplay.media

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.nav.AppNav
import com.hdvideoplay.media.ui.theme.MediaTheme

/**
 * Business shell only. Cold/hot splash and App Open are owned by SplashActivity
 * and HotStartManager; MainActivity is always entered after the splash finishes.
 */
class MainActivity : ComponentActivity() {
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
        // Preload pools for the next return; load is not show.
        AdScheduler.load(AdScenes.HOT_OPEN)
        AdScheduler.load(AdScenes.NORMAL_1)
        AdScheduler.load(AdScenes.NORMAL_2)
    }
}
