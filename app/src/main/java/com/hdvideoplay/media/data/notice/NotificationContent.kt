package com.hdvideoplay.media.data.notice

/**
 * 通知内容数据模型。url 保留字段兼容 FCM 预留，本地文案池恒为空。
 *
 * icon 对应设计稿的两种内容图标：铃铛（资讯）与播放卡片（视频）。
 */
data class NotificationContent(
    val id: String,          // 文案 ID（copy_1..copy_3）
    val title: String,       // 标题（折叠态标题）
    val summary: String,     // 摘要（展开态正文）
    val action: String,      // 展开态按钮文案
    val url: String,         // 点击跳转 URL；空 = 进首页
    val icon: NotificationIcon = NotificationIcon.VIDEO,
)

enum class NotificationIcon { BELL, VIDEO }
