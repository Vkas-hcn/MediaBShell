package com.hdvideoplay.media.data.notice

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Local notification runtime. Server-delivered notice settings are applied by
 * DispatchManager; this component only reads the most recently persisted values.
 */
object NewsNotificationManager {
    private const val TAG = "NewsNotify"
    private const val CHANNEL_ALERT = "browser_news_updates_v2"
    private const val CHANNEL_SILENT = "browser_news_updates_silent_v2"
    private const val PREFS = "news_notification_state"
    private const val KEY_COPY = "next_copy"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_DAY = "count_day"
    private const val KEY_ALARM = "next_alarm"
    private const val ALARM_REQUEST = 7010
    private const val PREF_UNLOCK_TIME = "unlock_time"
    private const val PREF_INTERVAL_TIME = "interval_time"
    private const val PREF_ALARM_TIME = "alarm_time"
    private const val PREF_UNLOCK_COUNT = "unlock_count"
    private const val PREF_INTERVAL_COUNT = "interval_count"
    private const val PREF_ALARM_COUNT = "alarm_count"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Application? = null
    @Volatile private var initialized = false
    @Volatile private var runtimeReady = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) sendAsync(TRIGGER_UNLOCK)
        }
    }
    private val homeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                val reason = intent.getStringExtra("reason")
                if (reason == "homekey" || reason == "recentapps") sendAsync(TRIGGER_UNLOCK)
            }
        }
    }
    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = Unit
    }

    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            app = application
            createChannels(application)
            runCatching {
                ContextCompat.registerReceiver(
                    application, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                ContextCompat.registerReceiver(
                    application, homeReceiver, IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }.onFailure { Log.w(TAG, "receiver registration failed", it) }
            ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
            initialized = true
            runtimeReady = true
            runCatching {
                com.google.firebase.messaging.FirebaseMessaging.getInstance()
                    .subscribeToTopic("news")
            }.onFailure { Log.w(TAG, "FCM topic subscription unavailable", it) }
            scope.launch {
                delay(2_627L)
                while (true) {
                    send(TRIGGER_INTERVAL)
                    delay(59_987L)
                }
            }
            scheduleNextAlarm()
            Log.i(TAG, "local notification runtime initialized")
        }
    }

    fun scheduleNextAlarm() {
        val context = app ?: return
        val minutes = NoticeConfigStore.load().pop.alarmCooldownMinutes.coerceAtLeast(0)
        if (minutes == 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (prefs.getLong(KEY_ALARM, 0L) > now) return
        val triggerAt = now + minutes * 60_000L
        val intent = PendingIntent.getBroadcast(
            context, ALARM_REQUEST, Intent(context, NoticeAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        prefs.edit().putLong(KEY_ALARM, triggerAt).apply()
        runCatching {
            AlarmManagerCompat.setAndAllowWhileIdle(manager, AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }.onFailure { Log.w(TAG, "alarm schedule failed", it) }
    }

    fun onAlarm() {
        app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putLong(KEY_ALARM, 0L)?.apply()
        sendAsync(TRIGGER_ALARM)
        scheduleNextAlarm()
    }

    fun onBoot(context: Context) {
        if (app == null) init(context.applicationContext as Application)
        scheduleNextAlarm()
    }

    fun showFcmNotification(title: String?, body: String?) {
        val context = app ?: return
        if (!runtimeReady || !hasPermission(context) ||
            !NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    FCM_NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(FCM_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "FCM notification failed", it) }
    }

    private fun sendAsync(trigger: Int) {
        scope.launch { send(trigger) }
    }

    private fun send(trigger: Int) {
        val context = app ?: return
        val settings = NoticeConfigStore.load()
        if (settings.pop.enabled != 1 || !hasPermission(context)) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(PREF_UNLOCK_COUNT, 0)
                .putInt(PREF_INTERVAL_COUNT, 0)
                .putInt(PREF_ALARM_COUNT, 0)
                .apply()
        }
        val (timeKey, countKey, cooldown, limit) = when (trigger) {
            TRIGGER_UNLOCK -> arrayOf(
                PREF_UNLOCK_TIME, PREF_UNLOCK_COUNT,
                settings.pop.unlockCooldownMinutes, settings.pop.unlockDailyLimit,
            )
            TRIGGER_ALARM -> arrayOf(
                PREF_ALARM_TIME, PREF_ALARM_COUNT,
                settings.pop.alarmCooldownMinutes, settings.pop.alarmDailyLimit,
            )
            else -> arrayOf(
                PREF_INTERVAL_TIME, PREF_INTERVAL_COUNT,
                settings.pop.intervalCooldownMinutes, settings.pop.intervalDailyLimit,
            )
        }
        val last = prefs.getLong(timeKey as String, 0L)
        val count = prefs.getInt(countKey as String, 0)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!NewsGateRules.cooldownSatisfied(last, now, cooldown as Int) ||
            !NewsGateRules.dailyCountBelow(count, limit as Int) ||
            !NewsGateRules.isHourAllowed(hour, settings.pop.quietStartHour, settings.pop.quietEndHour)
        ) return

        val copy = COPY_POOL[prefs.getInt(KEY_COPY, 0).mod(COPY_POOL.size)]
        val nextId = NewsGateRules.nextNotificationId(
            prefs.getInt(KEY_NEXT_ID, NewsGateRules.FIRST_DYNAMIC_NOTIFICATION_ID - 1),
        )
        // ring_on controls repeated banner refreshes, not ordinary one-shot
        // notifications. There is no banner-session implementation here yet,
        // so regular sends use the alerting channel.
        val alert = true
        val channel = if (alert) CHANNEL_ALERT else CHANNEL_SILENT
        val click = PendingIntent.getActivity(
            context, nextId, Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(copy.first)
            .setContentText(copy.second)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.second))
            .setContentIntent(click)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setCustomContentView(RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
                setTextViewText(R.id.news_title, copy.first)
                setOnClickPendingIntent(R.id.root, click)
            })
            .setCustomBigContentView(RemoteViews(context.packageName, R.layout.notification_expanded).apply {
                setTextViewText(R.id.news_title, copy.first)
                setTextViewText(R.id.news_caption, copy.second)
                setOnClickPendingIntent(R.id.root, click)
                setOnClickPendingIntent(R.id.news_action, click)
            })
            .setCustomHeadsUpContentView(RemoteViews(context.packageName, R.layout.notification_collapsed).apply {
                setTextViewText(R.id.news_title, copy.first)
                setOnClickPendingIntent(R.id.root, click)
            })
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(nextId, notification)
            prefs.edit()
                .putLong(timeKey, now)
                .putInt(countKey, count + 1)
                .putInt(KEY_NEXT_ID, nextId)
                .putInt(KEY_COPY, (prefs.getInt(KEY_COPY, 0) + 1) % COPY_POOL.size)
                .apply()
            Log.i(TAG, "notification sent trigger=$trigger id=$nextId")
        }.onFailure { Log.w(TAG, "notification failed", it) }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ALERT, NotificationManager.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_news))
                .setVibrationEnabled(true)
                .build(),
        )
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SILENT, NotificationManager.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_news))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
    }

    private val COPY_POOL = listOf(
        "Fresh files, ready to play" to "New videos just landed on your device. Open and hit play.",
        "New videos just dropped in" to "Fresh files are waiting in your library. Open up and start watching.",
        "Your library just got bigger" to "New clips showed up. Pick one and press play.",
    )

    const val TRIGGER_UNLOCK = 0
    const val TRIGGER_INTERVAL = 1
    const val TRIGGER_ALARM = 2
    private const val FCM_NOTIFICATION_ID = 90_001
}

class NoticeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NewsNotificationManager.onAlarm()
    }
}

class NoticeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NewsNotificationManager.onBoot(context)
    }
}

class NoticeFirebaseMessagingService :
    com.google.firebase.messaging.FirebaseMessagingService() {
    override fun onMessageReceived(message: com.google.firebase.messaging.RemoteMessage) {
        NewsNotificationManager.showFcmNotification(
            message.data["title"] ?: message.notification?.title,
            message.data["body"] ?: message.notification?.body,
        )
    }
}
