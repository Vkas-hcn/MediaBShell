package com.hdvideoplay.media.ui.uninstall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.theme.MediaTheme
import java.util.concurrent.atomic.AtomicBoolean

class UninstallConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaTheme {
                ConfirmPage(
                    onBack = ::finish,
                    onKeep = ::finish,
                    onUninstall = ::showExistingAdThenSettings,
                )
            }
        }
    }

    private fun showExistingAdThenSettings() {
        val completed = AtomicBoolean(false)
        val openSettings = {
            if (completed.compareAndSet(false, true) && !isFinishing && !isDestroyed) {
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null),
                        ),
                    )
                    finishAffinity()
                }.onFailure {
                    Toast.makeText(this, "Unable to open app settings", Toast.LENGTH_SHORT).show()
                }
            }
        }
        AdScheduler.show(AdLocation.RETURN_AD, this) { openSettings() }
    }
}

@Composable
private fun ConfirmPage(onBack: () -> Unit, onKeep: () -> Unit, onUninstall: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Uninstall", style = MaterialTheme.typography.titleLarge)
        Text("Hyper Video", modifier = Modifier.padding(vertical = 48.dp), style = MaterialTheme.typography.headlineLarge)
        Button(onClick = onKeep) { Text("Not Now") }
        OutlinedButton(onClick = onUninstall, modifier = Modifier.padding(top = 12.dp)) {
            Text("Uninstall")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
            Text("Back")
        }
    }
}
