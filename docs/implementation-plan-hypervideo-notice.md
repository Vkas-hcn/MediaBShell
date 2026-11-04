# HyperVideo 通知与热启动复刻实施记录

## 目标与边界

- 在本项目现有 Android 架构中接入通知配置、通知与频控、权限申请、热启动衔接及卸载挽留。
- 广告只通过当前广告门面复用；不移植 `inters_guide_1/2`，不修改广告加载/展示实现。
- Dispatch 的 `data` 是运行时权威配置；本地 `hd_config.json` 提供相同结构的默认配置。
- FCM 依赖需要可用的 Firebase 项目资源。仓库当前未发现 `google-services.json` 或 Firebase Messaging 依赖；配置缺失时不伪造凭据，记录为外部阻塞。

## 阶段

1. **基线与配置**：核对 HyperVideo 对接文档和本项目入口；扩展 Dispatch/本地配置模型，保留广告字段并加入 `notice.copop_config` 与 `notice.banner_notify`；增加解析测试。
2. **通知核心**：实现配置持久化、渠道/通知展示、文案轮换、冷却/日限/免打扰规则及闹钟/系统触发入口；FCM 仅在 Firebase 配置可用时接通。
3. **启动及权限**：保留 `HotStartManager`、`SplashActivity`、`AdScheduler` 链路，加入通知权限申请与首页二次拒绝说明，并验证冷/热启动不重复拉起。
4. **卸载挽留与验收**：加入桌面快捷方式入口、挽留/确认流程，广告仅调用已存在 API；运行编译、单元测试及可用的设备验证，回填本记录。

## 验收清单

- [ ] Dispatch 响应中的 `notice` 与广告配置均解析并被持久化；失败响应不覆盖最后一次成功配置。
- [ ] 本地默认与服务端 schema 一致，通知开关、频控和时段规则有测试。
- [ ] 权限授予、首次拒绝、再次拒绝、自定义说明及设置页返回路径可验证。
- [ ] 冷启动、热启动与通知触发不相互重复启动 Splash。
- [ ] 卸载挽留不调用 `inters_guide_1/2`，不改动广告加载/展示实现。
- [ ] 完整编译及项目测试通过；外部配置或设备验证受阻时明确记录。

## 执行记录

- 基线：应用已有 Dispatch 缓存、广告场景仓库、冷/热 Splash 和启动召回弹窗；尚无通知、通知权限、FCM、卸载挽留实现。
- 2026-11-03：确认仓库没有 Firebase Messaging 依赖及 `google-services.json`；当前给定 Dispatch 日志也未包含 `notice`。客户端 schema 和默认资产已纳入实现；端到端远端覆盖需服务端返回新字段后验证。
- 2026-11-03：已加入通知配置模型/SharedPreferences 缓存、通知发送与冷却/日限/时段门禁、开机/闹钟入口、通知权限请求与说明对话框、FCM service 壳及桌面静态快捷方式卸载挽留页。卸载页仅调用既有 `AdScheduler.show(RETURN_AD)`，未接入 guide 广告位。
- 验证：`:app:testDebugUnitTest` 与 `:app:assembleDebug` 通过（43 tasks）；第一次测试发现测试断言与 HyperVideo 的含端点允许时间窗定义不符，修正断言后通过。
- 外部/设备限制：当前仓库未见 `google-services.json`，因此 FCM token/topic/服务端投递不能端到端确认；通知权限对话框、真实通知投递、开机/闹钟和 launcher shortcut 尚未在设备上验证。现有 Dispatch 日志无 notice，需后端下发新字段才能确认远端覆盖。
- 尚未实现：HyperVideo 的完整 RemoteViews/FCM 共用样式、banner 重复会话与划除恢复、解锁/Home 防抖及埋点；热启动现有实现保持不变；当前卸载页面为原生 Compose 简化版本，没有复刻全部图形资源与导航转场。需在补齐资源和设备回归后才可称为完整视觉/行为复刻。

## 整改计划 v2（2026-11-03，已与需求方确认口径）

审查结论：配置模型/Dispatch 落盘/依赖/Manifest 骨架保留；通知 UI、通知权限申请逻辑、卸载页 UI 重写；补齐 banner 会话、FCM 三件套、保活 Job、热启动复刻与通知点击链路。

已确认口径：FCM 接入（google-services.json 已就位）；热启动完全对齐 HyperVideo（去掉直接展示分支，一律清栈重拉启动页，广告走现有 AdScheduler SPLASH_HOT）；卸载挽留两处插屏用现有 AdScheduler.show(RETURN_AD) 替代、挽留页原生广告容器保持 gone；跳过 UMP；通知点击→清栈进启动页→开屏广告→落首页；XML 照搬并以 ComponentActivity/TextView 替代 AppCompatActivity/MaterialButton。

五阶段：1 资源照搬+构建/Manifest 补齐；2 通知核心重写（HintUtil/门禁/banner 会话/文案/样式绑定/接收器/闹钟/Job）+FCM 三件套；3 通知权限重写（permission 包+300ms 判定+自定义弹框）；4 热启动复刻+通知点击/卸载入口落页；5 卸载挽留 UI 与流程重写+收尾。

### 阶段 1（2026-11-03，进行中）

- 资源照搬：notification_collapsed/collapsed_legacy/expanded、dialog_notification_permission（MaterialButton→TextView）、activity_uninstall_retention/confirm（GradientTextView 指本项目类名）；12 个 webp 图标与 uninstall_bg；mulish_semibold 字体；7 个 shape drawable；colors+values-night（notification_text_*）；strings 替换为 HyperVideo 原文案并补齐 uninstall_*；AppActionDialogTheme+PermissionActionButton；app_shortcuts（ic_delete_uninstall + com.hd.videoplay.media.delete + extra from=uninstall）。
- 构建：google-services 插件（4.4.2）根声明 + app 条件应用（google-services.json 存在即应用）；AdSceneRepository.loadFromAssets 落盘 notice（与 dispatch 对称）。
- Manifest：补 VIBRATE；UninstallEntryActivity 改 excludeFromRecents 并去掉 intent-filter。
- 偏差：NewsBannerDismissReceiver/AssistanceLaunceJob 的 Manifest 声明随阶段 2 类文件一起落地（避免悬空声明）。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过；提交 e18ca1a。

### 阶段 2（2026-11-03，完成）

- 删除 NewsNotificationManager.kt（自写简化实现），按 HyperVideo 组件清单在 data/notice 包重写：HintUtil（2627ms 预热+59987ms 轮询、动态注册 PhoneLockRe/HomeRecentsReceiver、markLing 幂等+随机 requestCode、门禁顺序：权限→系统开关→co_on→前台→冷却→日限→时段、双渠道、sticky+FLAG_NO_CLEAR+deleteIntent、振动 [0,300,200,300]、动态 ID news_notification_ids、news_notification_sent 埋点、startJob）；NewsGateRules（对齐 HyperVideo 原式，isHourAllowed 移回 NoticeConfigStore）；BannerNotificationSessionManager；NotificationContent/Repository（3 组英文文案+进程内去重）；NewsNotificationViewBinder；NewsLogger（TAG=NewsNotify）。
- 接收器/保活：PhoneLockRe（goAsync+1500ms）、HomeRecentsReceiver（homekey 1000ms/recentapps 3000ms/防抖 1000ms）、TimeWorkerRe（闹钟触发+markLing 续链，替换 NoticeAlarmReceiver）、NoticeBootReceiver（markLing+FCM 订阅）、NewsBannerDismissReceiver、AssistanceLaunceJob（jobId 4321）+ androidx/core/app/WebCanService.java。
- FCM 三件套（data/notice/fcm）：MessageRecv（topic 常量 FCM_TOPIC=news，服务端对接参数；启动/开机/令牌刷新三处订阅）、FcmNotificationGate、FcmNotificationDecision（runtimeReady 暂存+1–2s 随机延迟）、FcmNotificationPresenter（渠道 fcm_news_channel_v1、ID 90001、共享 ViewBinder、soundMode==1 静音、点击走 SplashActivity.createNotificationClickIntent+EXTRA_FROM_FCM），替换 NoticeFirebaseMessagingService 简化壳。
- MediaApp：单例 get()；HintUtil.init/startJob/FcmNotificationDecision.onRuntimeReady 均 runCatching 兜底。
- SplashActivity：新增 EXTRA_RETURN_TO_MAIN/EXTRA_FROM_FCM 常量与 createNotificationClickIntent 工厂（落页处理在阶段 4）。
- HotStartManager：最小增量 processInForeground+isAppForeground()（阶段 4 重写时保留该 API）。
- 测试：移植 NewsGateRulesTest、BannerNotificationSessionManagerTest、FcmNotificationGateTest（替换旧版 NewsGateRulesTest 断言）；新增 NoticeConfigTest（解析/默认/回退/isHourAllowed）。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过。
### 阶段 3（2026-11-03，完成）

- 新建 permission 包：NotificationPermissionHelper（object+Host、registerForActivityResult 双 launcher、300ms SYSTEM_DIALOG_VISIBLE_MIN_MS 判定、requestNotification(systemOnly)、showNotificationInAppPrompt、skipNextHomeNotificationPrompt、纯函数三件套；Allow→先摘 dismiss 监听→HotStartManager.ignoreNextBackgroundReturn(notification_permission_settings)→设置页→返回回调 hasNotificationPermission）、NotificationPermissionDialog（AppActionDialogTheme、setLayout 屏宽−2×16dp、可取消、OnDismissListener 统一按拒绝）。
- 删除 MainActivity/SplashActivity 自写的 denials 计数、AlertDialog、homePermissionRequested、splash_asked 逻辑。
- MainActivity：permissionHost 惰性绑定（onCreate 早于 STARTED）；onResume→scheduleHomeNotificationPrompt（300ms 防抖）、onPause 移除；showHomeNotificationPrompt（仅 RESUMED 未销毁→showNotificationInAppPrompt{ granted→HintUtil.markLing() }）；新增 onHomeTabSelected()。
- AppNav：HOME Tab 点击回调 onHomeTabSelected()（每次切回首页重新判定）。
- SplashActivity：notifRequested 实例标志 + permissionHost.requestNotification(systemOnly=true)（同意/拒绝/返回键均不阻塞广告流程）。
- HotStartManager：最小增量 ignoreNextBackgroundReturn(reason)（onStop 清标记并跳过热启动武装；阶段 4 重写时保留该 API）。
- 测试：移植 NotificationPermissionRulesTest；验证 :app:assembleDebug + :app:testDebugUnitTest 通过。
### 阶段 4（2026-11-03，完成）

- HotStartManager 整体重写为 HyperVideo 语义：onStop（processInForeground=false→dismissCurrentAdActivity→ignoreNextBackgroundReturn 检查→hasSeenForeground 检查→forcePreloadHot+300ms scheduleArm→armed+finishNonWhitelisted）；onStart（cancelPendingArm→shouldLaunch=armed&&hasSeenForeground→post launchReturnToGuide）；launchReturnToGuide（屏幕未点亮跳过、栈顶 SplashActivity 跳过、createReturnToMainIntent NEW_TASK|CLEAR_TASK、失败回退 launchPlainHotStart SINGLE_TOP）；新增 ActivityLifecycleCallbacks（adActivity 弱引用 + contentActivities 集合）、relaunchGuideForHotStart（300ms 延迟清栈重拉）；删除直接展示分支；保留 isAppForeground() 与 ignoreNextBackgroundReturn()。
- SplashActivity 增强：EXTRA_RETURN_TO_MAIN/EXTRA_FROM_FCM 常量与 createReturnToMainIntent/createHotStartIntent/createNotificationClickIntent 工厂；进程级 startupEpoch/processAlreadyEntered；onCreate：reportNotificationClickIfNeeded（CO_SHOW_TYPE→HintUtil.cancelNewsNotification；实例内一次 news_notification_click 埋点；EXTRA_FROM_FCM 也上报）+ 热启动且广告展示中→startupEpoch++→restartStartupAfterDismissingAd（50ms 轮询、1500ms 上限）；onNewIntent（setIntent+通知点击处理+EXTRA_HOT_START→离场中 relaunchGuideForHotStart/否则 epoch++ 收起广告重走）；冷/热广告流程加 epoch 代数守卫；doExit 落页分支（isUninstallEntry→UninstallRetentionActivity；intent.data→ACTION_VIEW 系统浏览器；isHotStart&&!isReturnToMain→finish；否则→MainActivity）；requestNotificationThenProceed 增加 isUninstallEntry 跳过。
- 新增 ui/uninstall/UninstallEntry.kt（ACTION_DELETE_ENTRY=com.hd.videoplay.media.delete + EXTRA_UNINSTALL_ENTRY 判定）。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过；设备手测待阶段 5 收尾时一并走查。
### 阶段 5（2026-11-03，完成）

- 删除 Compose 简化版 Retention/Confirm 页面，改为 ComponentActivity + XML 照搬：UninstallRetentionActivity（insets 处理、返回/Keep watching/Fix/Speed up→MainActivity NEW_TASK|CLEAR_TASK、Still uninstalling→AdScheduler.show(RETURN_AD)→确认页、ad_container 保持 gone）、UninstallConfirmActivity（GradientTextView 品牌渐变、返回→finish、Not Now→MainActivity CLEAR_TASK、Uninstall→二次 RETURN_AD→应用详情设置页→finishAffinity、失败 Toast uninstall_settings_failed）。
- 两处插屏保持只展示缓存/被拦截立即回调：AdScheduler.show 无缓存同步回调 false→AtomicBoolean 幂等→流程继续，不阻塞卸载路径。
- GradientTextView（com.hdvideoplay.media.ui，继承 android.widget.TextView）。
- UninstallEntryActivity 改中转：NEW_TASK|CLEAR_TASK 转 SplashActivity（action=com.hd.videoplay.media.delete + EXTRA_UNINSTALL_ENTRY=true）；启动页识别 isUninstallEntry→跳过权限→冷启动开屏→挽留页。
- Manifest：挽留/确认页补 screenOrientation=portrait。
- 测试：移植 UninstallFlowTest（UninstallEntry 判定部分）。
- 全量回归：:app:assembleDebug + :app:testDebugUnitTest 通过。

## 整改验收清单（对照复刻对接文档 §8.5）

- [x] 通知：三类触发+Home/最近任务延迟防抖+门禁顺序+动态 ID 70_000+文案轮换与去重+闹钟自恢复+保活 Job
- [x] 通知样式：双渠道+collapsed/collapsed_legacy/expanded/heads-up RemoteViews+深浅色+inter 字体（与 HyperVideo 布局/图标/色值一致）
- [x] 频控：cooldown/daily/hour 三轴+跨天清零（NewsGateRules 原式，原版单测通过）
- [x] FCM：data-only→1–2s 随机延迟→门禁（初始化/非前台/权限）→90001 fcm_news_channel_v1 共享样式→soundMode 静音（topic=news 为服务端对接参数）
- [x] 热启动：300ms 防抖武装+CLEAR_TASK 拉起+SINGLE_TOP 回退+屏幕未点亮/已在启动页跳过+ignoreNextBackgroundReturn
- [x] 热启动广告：启动页 SPLASH_HOT 路径（AdScheduler 零改动），广告结束落 MainActivity
- [x] 通知权限：启动页每次实例 systemOnly+首页每次显示重试系统框+300ms 判定+二次拒绝自定义框（样式照搬）跳设置页+授权后 markLing
- [x] 卸载挽留：快捷方式→中转→启动页分流（跳过权限）→开屏→挽留页（XML 照搬）→RETURN_AD→确认页（XML 照搬+品牌渐变）→二次 RETURN_AD→设置页→finishAffinity
- [x] 下发配置：notice 节点解析落盘+DTO 默认值=用户 JSON+解析失败回退默认
- [x] 全部提交编译通过+单测全绿+docs 计划文档同步

## 设备验证待办（代码侧已完成，需真机回归）

- 通知：真机投递（轮询/解锁/Home）、常驻 banner 会话（banner_on=1 时）、通知点击清栈落首页、划掉恢复。
- 热启动：后台 300ms 后回前台清栈重走开屏；去通知设置页返回不重走开屏；屏幕未点亮跳过。
- 权限：启动页系统框、首页连续两次拒绝后自定义框、Allow→设置页→返回 markLing。
- 卸载挽留：长按图标→Uninstall→启动页→开屏→挽留页→确认页→设置页全链路（含两处 RETURN_AD 插屏）。
- FCM：需推送端按 topic=news 发送 data-only 消息后验证 1–2s 延迟展示与点击落首页。
## 扩展：通知封面影视化（2026-11-03，已与需求方确认口径）

需求：通知样式中的固定图片改为随机拉取应用内影视资源的封面；点击通知先走启动页展示开屏广告，然后进入对应封面 id 的视频详情页并播放。

已确认口径：数据源=首页 home_data（模块展平）；拉取失败回退现有固定图标+静态文案；文案=标题用影片标题、摘要用简介、按钮 Play now；点击→启动页→开屏广告→DetailActivity(mediaId, autoPlay=true)；拉取策略=缓存 5 条池子 1 小时 TTL、进程内随机轮换去重。

- 数据层（提交 9507fe0）：NotificationContent 增加 mediaId/posterUrl；NotificationContentRepository 增加动态池（home_data 展平→过滤有 mediaId/标题/封面的条目→随机 5 条缓存 1h→每次随机且避免连续重复），失败回退静态池；PosterFetcher（OkHttp 下载、128px 小图/360px 大图双解码、按 URL 进程内缓存≤8 条、5s 超时失败回退固定图标）；NotificationContentRepositoryTest（fake MediaApi：字段映射/单条去重/TTL 复用与过期重拉/失败回退）。
- 构建与落页（本提交）：NewsNotificationViewBinder 增加 poster 参数（封面优先、固定图标兜底）；HintUtil 发送链路透传封面（普通与 banner 会话两条路径）；通知点击 intent 携带 EXTRA_MEDIA_ID；SplashActivity.doExit 增加 mediaId 分支→DetailActivity.createIntent(autoPlay=true)。FCM 通知样式与点击行为不变。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过。设备待办：真机验证封面显示与点击→开屏→详情页自动播放链路。
## 扩展：启动页进度条与广告等待窗口同步（2026-11-04）

需求：启动页底部进度条最大值与 COLD_AD_WAIT_MS / HOT_AD_WAIT_MS 同步——广告展示出来或超时跳转首页的那一刻，进度条恰好走到尽头。

- SplashLoadingScreen 改为「可重定向进度」：总时长 = hold + 等待窗口（splashWindowMs()，由 COLD/HOT_AD_WAIT_MS 常量计算，改常量自动同步）；SplashActivity 通过 MutableStateFlow<Long> 下发目标时刻（elapsedRealtime），composable 用 Animatable 按当前进度快照校准后线性走满；目标收拢时自动加速（重定向），目标已过则立即走满。
- 收拢时机：冷/热流程检测到广告就绪 → completeProgressSoon()（收拢到此刻 + 150ms 展示缓冲，覆盖 awaitResumed/emit 间隙）；热启动被频控拦截 → completeProgressNow()（立即走满与跳转同步）；等待窗口超时退出 → 无需处理（进度条天然在窗口终点走满）。
- 保留原有语义：hold 最短停留（1400ms）后触发 onHoldElapsed（原 onSplashFinished）启动广告流程；loading_show 埋点、E12/E13 埋点、重启流程（restartStartupFlow 重置 startedAt 与目标）均不受影响。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过；设备待办：观察冷启动（最长约 11.9s）与热启动（最长约 11.6s）进度条是否在广告弹出/跳转瞬间走满。
## 扩展：notice.news_list 远端下发（2026-11-04，方案已确认）

需求：notice 节点新增内容列表 news_list（[{media_id, title, overview, poster_url, action}]），实现通知文案、电影跳转、电影封面的动态下发；media_id 未填随机选片（现有随机池）；media_id 有、poster_url 无则按 media_id 查详情补海报。

- 模型（提交 02bffac）：NoticeNewsItem DTO + NoticeConfig.newsList；本地 hd_config.json notice 增加 news_list: []（空=现有行为）；服务端下发经既有链路（DispatchManager→NoticeConfigStore 整体覆盖）自动生效。
- 内容选取（提交 88272dd）：NotificationContentRepository 优先级改为 远端 news_list（按序轮换）> home_data 随机池 > 静态兜底；media_id<=0 条目走随机池；单条列表允许连续重复（受频控约束）、多条轮换天然去重；标题/简介/海报任一缺失时按 media_id 调 detail 接口补全（5s 超时、异常兜住），补全后仍无标题回退随机池，仅缺海报→固定图标兜底；action 空默认 Play now。
- 零改动：DispatchManager、HintUtil、PosterFetcher、ViewBinder、SplashActivity 点击落页（EXTRA_MEDIA_ID→详情页自动播放）、FCM 链路。
- 验证：:app:assembleDebug + :app:testDebugUnitTest 通过（86 用例 0 失败；测试目录按仓库 .gitignore 约定不提交）。设备待办：服务端下发 news_list 后验证轮换展示、海报补全与点击落详情播放。
### news_list 生效问题修复（2026-11-04，提交 db914f4）

现象：本地 hd_config.json 修改 news_list 后，通知仍展示随机电影（旧逻辑）。

原因：
1. DispatchManager 启动时用缓存的 dispatch payload、以及拉取成功时用远端 notice 覆盖落盘（NoticeConfigStore 整体覆盖）；远端 payload 无 news_list → DTO 默认空列表 → 本地列表被清空。
2. 远端列表轮换游标只在进程内存，进程重启归零 → 每次冷启动第一条永远是列表首项（若首项 media_id=0 则随机电影）。

修复：
- NoticeConfigStore.save 增加 mergeNewsList：下发/缓存未携带 news_list（空列表）时保留已生效列表；非空列表仍以远端为准（远端可覆盖，但暂时无法用空列表清空，需显式清空能力时再加字段）。
- NotificationContentRepository 轮换游标持久化到 notification_copy_rotation/remote_index（跨进程累计；列表变更时按新长度取模对齐）。

注意：按已确认口径，media_id=0 的条目会忽略下发文案与封面、随机选片；要展示指定文案/封面必须填 media_id（poster_url 可省，缺省时按 media_id 查详情补全）。
