package com.hdvideoplay.media.data.notice

/** Deterministic notification gates kept platform-free for unit testing. */
internal object NewsGateRules {
    const val FIRST_DYNAMIC_NOTIFICATION_ID = 70_000

    fun cooldownSatisfied(lastSentAtMs: Long, nowMs: Long, cooldownMinutes: Int): Boolean =
        lastSentAtMs <= 0L || cooldownMinutes.coerceAtLeast(0) * 60_000L <= nowMs - lastSentAtMs

    fun dailyCountBelow(count: Int, limit: Int): Boolean = count < limit.coerceAtLeast(0)

    fun isHourAllowed(hour: Int, quietStartHour: Int, quietEndHour: Int): Boolean {
        val from = minOf(quietStartHour, quietEndHour).coerceIn(0, 23)
        val through = maxOf(quietStartHour, quietEndHour).coerceIn(0, 23)
        return hour in from..through
    }

    fun nextNotificationId(lastId: Int): Int =
        if (lastId in FIRST_DYNAMIC_NOTIFICATION_ID until Int.MAX_VALUE) lastId + 1
        else FIRST_DYNAMIC_NOTIFICATION_ID
}
