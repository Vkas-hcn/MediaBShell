package com.hdvideoplay.media.data.notice.fcm

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hdvideoplay.media.MediaApp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.notice.NewsLogger
import com.hdvideoplay.media.data.notice.NewsNotificationViewBinder
import com.hdvideoplay.media.data.notice.NotificationContent
import com.hdvideoplay.media.data.notice.NotificationIcon
import com.hdvideoplay.media.ui.splash.SplashActivity

internal object FcmNotificationPresenter {
    internal const val CHANNEL_ID = "fcm_news_channel_v1"
    internal const val NOTIFICATION_ID = 90_001
    private const val TAG = "FcmNews"

    fun show(content: FcmNewsContent, soundMode: Int): Boolean {
        val context = MediaApp.get()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            NewsLogger.fcmPermissionCancelled(content.id)
            return false
        }
        createChannel()
        val silent = soundMode == 1
        val clickIntent = SplashActivity.createNotificationClickIntent(context).apply {
            action = Intent.ACTION_VIEW
            content.link.takeIf { it.isNotBlank() }?.let { data = Uri.parse(it) }
            putExtra(SplashActivity.EXTRA_FROM_FCM, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = if (content.type.contains("news", ignoreCase = true)) {
            NotificationIcon.BELL
        } else {
            NotificationIcon.VIDEO
        }
        val viewContent = NotificationContent(
            id = content.id,
            title = content.title,
            summary = content.body,
            action = content.cta.ifBlank { "Open" },
            url = content.link,
            icon = icon,
        )
        val collapsed = NewsNotificationViewBinder.collapsed(context, viewContent, pendingIntent)
        val expanded = NewsNotificationViewBinder.expanded(context, viewContent, pendingIntent)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(ContextCompat.getColor(context, R.color.brand_red))
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
            .setSilent(silent)
            .setContentIntent(pendingIntent)
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setCustomHeadsUpContentView(collapsed)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        }.onFailure { error ->
            Log.e(TAG, "FCM submit exception", error)
        }.getOrDefault(false)
    }

    private fun createChannel() {
        val context = MediaApp.get()
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_news))
                .build(),
        )
    }
}
