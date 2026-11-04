package com.hdvideoplay.media.ads

import android.util.Log

/**
 * 广告统一日志（logcat TAG = [MediaAd]）。
 *
 * 统一中文输出，覆盖：请求加载 / 加载成功 / 加载失败 / 展示 / 关闭 / 点击 / 频控 / 成对。
 * 格式为 【广告位中文名】事件,细节；未收录的 key 原样输出，方便和后台配置对照。
 * 排查时用 logcat 过滤 tag MediaAd 即可看到完整链路。
 */
object AdLogger {

    private const val TAG = "MediaAd"
    var enabled = true

    // ---------------- SDK / 全局 ----------------

    fun sdkInitialized() = d("【全局】AdMob SDK 初始化完成")

    // ---------------- 加载（SceneController 瀑布） ----------------

    fun loadStart(scene: String, format: String) =
        d(prefix(scene) + "开始加载,格式:" + formatName(format))

    fun loadBlocked(scene: String, reason: String) =
        d(prefix(scene) + "跳过加载,原因:" + reasonText(reason))

    fun waterfallTry(scene: String, index: Int, platform: String, format: String, unitId: String) =
        d(prefix(scene) + "尝试第 " + (index + 1) + " 个广告单元:" + platform + "(" + unitId + ")")

    fun loadSuccess(scene: String, format: String, unitId: String) =
        d(prefix(scene) + "加载成功,格式:" + formatName(format) + ",广告单元:" + unitId)

    fun loadFailed(scene: String, format: String, unitId: String, reason: String) =
        w(prefix(scene) + "加载失败,格式:" + formatName(format) + ",广告单元:" + unitId + ",原因:" + reasonText(reason))

    fun waterfallExhausted(scene: String) =
        w(prefix(scene) + "全部广告单元加载失败,本次不展示")

    // ---------------- 展示（AdScheduler / Loader / AdActivity） ----------------

    fun showRequest(location: String) =
        d(prefix(location) + "请求展示")

    fun showBlocked(location: String, reason: String) =
        w(prefix(location) + "展示被阻止,原因:" + reasonText(reason))

    fun showEmpty(location: String) =
        d(prefix(location) + "无已就绪广告(load≠show),本次不展示")

    fun showStart(location: String, scene: String, format: String, unitId: String, priority: Int) =
        d(prefix(location) + "展示开始,场景:" + slotName(scene) + ",格式:" + formatName(format) + ",单元:" + unitId + ",优先级:" + priority)

    fun showPresented(location: String, format: String, unitId: String) =
        i(prefix(location) + "展示成功,格式:" + formatName(format) + ",单元:" + unitId)

    fun showDismissed(location: String, format: String, unitId: String) =
        d(prefix(location) + "已关闭,格式:" + formatName(format) + ",单元:" + unitId)

    fun showFailed(location: String, format: String, reason: String) =
        w(prefix(location) + "展示失败,格式:" + formatName(format) + ",原因:" + reasonText(reason))

    fun adClicked(location: String, format: String) =
        d(prefix(location) + "被点击,格式:" + formatName(format))

    // ---------------- 缓存 / 消耗 ----------------

    fun consume(scene: String, unitId: String, refill: Boolean) =
        d(prefix(scene) + "消耗一条广告,单元:" + unitId + ",是否补充加载:" + yesNo(refill))

    fun cacheExpired(scene: String, unitId: String, ageMs: Long, ttlMs: Long) =
        d(prefix(scene) + "缓存已过期,单元:" + unitId + ",已缓存 " + ageMs / 1000 + " 秒,有效期 " + ttlMs / 1000 + " 秒")

    // ---------------- 频控 ----------------

    fun intervalBlocked(location: String, sinceLastMs: Long, intervalMs: Long) =
        w(prefix(location) + "距上次展示 " + sinceLastMs / 1000 + " 秒,不足最小间隔 " + intervalMs / 1000 + " 秒")

    // ---------------- 成对 / 返回 ----------------

    fun pairedStart(reason: String) =
        d("【成对】开始,触发原因:" + reason)

    fun pairedShellShown() = d("【成对】第1段全屏原生壳已展示")
    fun pairedMidcardShown() = d("【成对】第2段中卡已展示")

    fun pairedComplete(reason: String, shell: Boolean, mid: Boolean) =
        d("【成对】完成,触发原因:" + reason + ",全屏=" + yesNo(shell) + ",中卡=" + yesNo(mid))

    fun pairedMiss(reason: String) =
        w("【成对】无就绪库存,触发原因:" + reason + ",本次不展示")

    fun progressBoundary(n: Int, boundaryMs: Long, positionMs: Long) =
        d("【进度边界】跨过第 " + n + " 档(" + boundaryMs / 60000 + " 分钟),当前进度 " + positionMs / 1000 + " 秒")

    fun seekForwardBoundaries(fromMs: Long, toMs: Long, crossed: List<Int>) =
        d("【进度边界】快进 " + fromMs / 1000 + "→" + toMs / 1000 + " 秒,新跨越边界=" + crossed)

    fun returnNoAd(page: String, dwellMs: Long) =
        d("【返回】页面:" + page + ",停留 " + dwellMs / 1000 + " 秒 <60秒,不展示广告")

    fun returnPaired(page: String, dwellMs: Long) =
        d("【返回】页面:" + page + ",停留 " + dwellMs / 1000 + " 秒 ≥60秒,展示成对广告")

    // ---------------- 文案映射 ----------------

    private fun prefix(slot: String) = "【" + slotName(slot) + "】"

    private fun yesNo(b: Boolean) = if (b) "是" else "否"

    private fun formatName(format: String): String = when (format) {
        "launch_screen" -> "开屏"
        "native" -> "原生"
        "interstitial_ad" -> "插屏"
        else -> format
    }

    private fun slotName(raw: String): String = when (raw) {
        // 加载场景
        "cold_open" -> "冷启动开屏"
        "hot_open" -> "热启动开屏"
        "normal_1" -> "常规1"
        "normal_2" -> "常规2"
        // 展示位置
        "first_splash_cold" -> "首次冷启动开屏"
        "splash_cold" -> "冷启动开屏"
        "splash_hot" -> "热启动开屏"
        "return_ad" -> "返回中卡"
        "fullscreen_shell" -> "全屏原生壳"
        "uninstall_retention" -> "卸载挽留"
        else -> raw
    }

    private fun reasonText(reason: String): String = when (reason) {
        "activity_blacklisted" -> "页面已销毁"
        "trigger_gap_not_met" -> "冷启动静默期内"
        "another_fullscreen" -> "已有其他全屏广告展示中"
        "interval_not_met" -> "距上次展示不足最小间隔"
        "already_ready" -> "已有就绪广告"
        "load_in_flight" -> "已有加载任务进行中"
        "no_nodes" -> "无广告节点"
        "hot_skip_cold_ready" -> "冷池仍有就绪广告"
        "timeout" -> "加载超时"
        "no_loader" -> "无对应加载器"
        "payload_missing" -> "广告对象缺失"
        "activity_finishing" -> "页面正在销毁"
        else -> reason
    }

    private fun d(message: String) { if (enabled) Log.d(TAG, message) }
    private fun i(message: String) { if (enabled) Log.i(TAG, message) }
    private fun w(message: String) { if (enabled) Log.w(TAG, message) }
}
