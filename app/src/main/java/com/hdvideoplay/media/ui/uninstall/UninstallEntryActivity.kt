package com.hdvideoplay.media.ui.uninstall

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Static launcher shortcut destination; immediately forwards into the retention flow. */
class UninstallEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, UninstallRetentionActivity::class.java))
        finish()
        overridePendingTransition(0, 0)
    }
}
