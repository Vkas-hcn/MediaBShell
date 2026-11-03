package com.hdvideoplay.media.data.notice.fcm

import com.hdvideoplay.media.data.notice.HintUtil
import com.hdvideoplay.media.data.notice.NewsLogger
import com.hdvideoplay.media.startup.HotStartManager
import com.hdvideoplay.media.track.TrackEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

internal data class FcmNewsContent(
    val id: String,
    val title: String,
    val body: String,
    val cta: String,
    val link: String,
    val type: String,
)

/**
 * FCM Topic 专属触发与判断。
 * 延迟：收到后随机 1–2s。
 * 门禁：运行时已初始化、非应用前台、通知权限。
 * 不依赖运营 Config 总开关、频控或允许时段。
 */
internal object FcmNotificationDecision {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var runtimeReady = false

    @Volatile
    private var pendingPayload: Map<String, String>? = null

    fun onMessage(data: Map<String, String>) {
        if (!runtimeReady) {
            pendingPayload = HashMap(data)
            NewsLogger.fcmStaged(data.size)
            return
        }
        scope.launch { process(data, source = "消息到达") }
    }

    fun onRuntimeReady() {
        runtimeReady = true
        val data = pendingPayload ?: return
        pendingPayload = null
        scope.launch { process(data, source = "运行时就绪") }
    }

    private suspend fun process(data: Map<String, String>, source: String) {
        val delayMs = Random.nextLong(DELAY_MIN_MS, DELAY_MAX_MS + 1)
        NewsLogger.fcmProcessingStart(source, delayMs)
        delay(delayMs)

        val content = contentFromPayload(data)
        val soundMode = soundModeFromPayload(data)

        val gate = evaluateGateReason()
        if (gate != null) {
            NewsLogger.fcmBlocked(gate, detail = ",应用在前台=" + HotStartManager.isAppForeground())
            return
        }

        // E14 notification_trigger：FCM 触发逻辑真正执行、准备生成通知（NOTIFY-BASE）。
        TrackEvents.notificationTrigger("fcm", "fcm")
        val shown = FcmNotificationPresenter.show(content = content, soundMode = soundMode)
        if (!shown) {
            // 触发后因权限/系统限制未送达：trigger 已上报、send 不上报。
            NewsLogger.fcmSubmitFailed(content.id)
            return
        }
        // notification_send：已成功提交给系统通知管理服务；E16 实际展示。
        TrackEvents.notificationSend()
        TrackEvents.notificationShow("fcm", "fcm")
        NewsLogger.fcmShown(content.id)
    }

    private suspend fun evaluateGateReason(): String? =
        FcmNotificationGate.blockReason(
            runtimeInitialized = runtimeReady,
            isForeground = HotStartManager.isAppForeground(),
            hasNotificationPermission = HintUtil.coHavePermission(),
        )

    private fun contentFromPayload(data: Map<String, String>): FcmNewsContent =
        FcmNewsContent(
            id = (data["type"] ?: "").take(40).ifBlank { "topic" },
            title = data["title"].orEmpty(),
            body = data["body"].orEmpty(),
            cta = data["cta"].orEmpty(),
            link = data["link"].orEmpty(),
            type = data["type"].orEmpty(),
        )

    private fun soundModeFromPayload(data: Map<String, String>): Int =
        data["soundMode"]?.toIntOrNull()?.takeIf { it == 0 || it == 1 } ?: 0

    private const val DELAY_MIN_MS = 1_000L
    private const val DELAY_MAX_MS = 2_000L
}
