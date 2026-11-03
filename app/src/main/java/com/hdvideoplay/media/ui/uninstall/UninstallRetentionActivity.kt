package com.hdvideoplay.media.ui.uninstall

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ads.AdLocation
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ui.theme.MediaTheme
import java.util.concurrent.atomic.AtomicBoolean

class UninstallRetentionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaTheme {
                RetentionPage(
                    onKeep = ::finish,
                    onContinue = ::showExistingAdThenConfirm,
                )
            }
        }
    }

    private fun showExistingAdThenConfirm() {
        val completed = AtomicBoolean(false)
        val proceed = {
            if (completed.compareAndSet(false, true) && !isFinishing && !isDestroyed) {
                startActivity(Intent(this, UninstallConfirmActivity::class.java))
            }
        }
        // Reuses the already-defined return-ad API and inventory; never loads or
        // presents the excluded guide_1/guide_2 placements.
        AdScheduler.show(AdLocation.RETURN_AD, this) { proceed() }
    }
}

@Composable
private fun RetentionPage(onKeep: () -> Unit, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Feedback", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Problems during use",
            modifier = Modifier.padding(top = 30.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Improve your experience",
            modifier = Modifier.padding(top = 8.dp, bottom = 28.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Video playback issue                         Fix")
        Text("Video loading is slow                 Speed up", modifier = Modifier.padding(top = 14.dp))
        Button(onClick = onKeep, modifier = Modifier.padding(top = 36.dp)) {
            Text("Keep watching")
        }
        OutlinedButton(onClick = onContinue, modifier = Modifier.padding(top = 8.dp)) {
            Text("Still uninstalling")
        }
    }
}
