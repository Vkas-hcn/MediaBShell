package com.hdvideoplay.media.data.notice

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews
import com.hdvideoplay.media.R

/**
 * 通知 RemoteViews 的唯一组装入口：本地新闻通知与 FCM 通知共用，
 * 保证两种来源的折叠/展开样式与设计稿一致。
 * 传入 [poster]（影视封面）时通知图使用封面，否则回退固定图标。
 */
internal object NewsNotificationViewBinder {

    fun collapsed(
        context: Context,
        content: NotificationContent,
        contentIntent: PendingIntent,
        poster: Bitmap? = null,
    ): RemoteViews {
        val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            R.layout.notification_collapsed
        } else {
            R.layout.notification_collapsed_legacy
        }
        return RemoteViews(context.packageName, layout).also { view ->
            if (poster != null) {
                view.setImageViewBitmap(R.id.news_icon, poster)
            } else {
                view.setImageViewResource(R.id.news_icon, content.icon.smallIconRes())
            }
            view.setTextViewText(R.id.news_title, content.title)
            view.setOnClickPendingIntent(R.id.root, contentIntent)
            view.setOnClickPendingIntent(R.id.news_play, contentIntent)
        }
    }

    fun expanded(
        context: Context,
        content: NotificationContent,
        contentIntent: PendingIntent,
        poster: Bitmap? = null,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_expanded).also { view ->
            if (poster != null) {
                view.setImageViewBitmap(R.id.news_hero, poster)
            } else {
                view.setImageViewResource(R.id.news_hero, content.icon.largeIconRes())
            }
            view.setTextViewText(R.id.news_title, content.title)
            view.setTextViewText(R.id.news_caption, content.summary)
            view.setTextViewText(R.id.news_action, content.action)
            view.setOnClickPendingIntent(R.id.root, contentIntent)
            view.setOnClickPendingIntent(R.id.news_action, contentIntent)
        }

    private fun NotificationIcon.smallIconRes(): Int = when (this) {
        NotificationIcon.BELL -> R.drawable.ic_notify_bell_small
        NotificationIcon.VIDEO -> R.drawable.ic_notify_video_small
    }

    private fun NotificationIcon.largeIconRes(): Int = when (this) {
        NotificationIcon.BELL -> R.drawable.ic_notify_bell_large
        NotificationIcon.VIDEO -> R.drawable.ic_notify_video_large
    }
}
