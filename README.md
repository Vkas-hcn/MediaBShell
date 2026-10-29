# Media B Shell（一期）

HaloDrift 式长视频 B 面壳。包名 / applicationId：`com.hdvideoplay.media`。  
Base URL：`https://stage.hdvideoplay.top`，接口前缀 `/api/media/`（路径保留尾斜杠）。

一期范围：首页模块、频道入口、搜索/Discover、模块分页、Detail Intent 占位。  
**不做**：PSSDK、广告瀑布、AF/Deeplink 门闸、反馈、鉴权。

## 编译

```bash
cd /workspace/media-b-shell
ANDROID_HOME=/home/box/Android/Sdk ./gradlew :app:assembleDebug
```

单元测试（海报兜底）：

```bash
ANDROID_HOME=/home/box/Android/Sdk ./gradlew :app:testDebugUnitTest
```

## 包结构

```
com.hdvideoplay.media
├── MainActivity / MediaApp
├── data
│   ├── api      MediaApi, NetworkModule
│   ├── dto      ApiResult, MediaItemDto, Detail/Links DTOs…
│   └── repo     MediaRepository
├── ui
│   ├── home / channel / search / module
│   ├── detail   DetailActivity（占位，员工2 接手）
│   ├── nav      AppNav 底栏 + 模块路由
│   ├── components / theme
└── util         PosterUtils（search 海报兜底）
```

## Detail Intent 契约（员工2 必读）

Activity：`com.hdvideoplay.media.ui.detail.DetailActivity`

| Extra | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `EXTRA_MEDIA_ID` | `Int` | 是 | 本地 `Media.id`（列表字段 `media_id`；daily 用 `id`） |
| `EXTRA_SEASON` | `Int` | 否 | 季号；电影通常不传 |
| `EXTRA_EPISODE` | `Int` | 否 | 集号；**电影默认 `0`**，电视剧从 `1` 起 |

工厂方法：

```kotlin
DetailActivity.createIntent(context, mediaId = 42, season = 1, episode = 1)
```

常量名（与代码一致）：

- `DetailActivity.EXTRA_MEDIA_ID`
- `DetailActivity.EXTRA_SEASON`
- `DetailActivity.EXTRA_EPISODE`

## 与员工2 的分工边界

**员工1（本壳，已完成）**

- 网络层 OkHttp + Retrofit + kotlinx.serialization
- `ApiResult`（`code` int/string 兼容）
- 列表向 API：`home_data` / `home_stream_data` / `streaming` / `module_data` / `discover` / `filter/options` / `search` / `daily`
- Compose：Home / Channel / Search+Discover / My 占位 / Module 分页
- 底栏四 Tab（英文）：Home | Channel | Search | My
- Detail **占位页**（只展示 Intent 参数）
- `PosterUtils` + unit test（空路径或仅 `…/w500` → 占位图）

**员工2（播放详情）**

- 替换 `DetailActivity` 正文：调用已声明的 `detail/`、`outside/links/`、`play_report/`
- Media3/ExoPlayer：多线路、自定义 headers、外挂字幕、失败切换 + 上报
- DTO 已在 `data/dto/DetailDto.kt` 预留；接口已在 `MediaApi` 声明

## 已知限制

- Stage 无鉴权；`search/` 海报后端可能只返回 `…/w500`（客户端已兜底）
- `module_data` 必须传 `page`；英语回退在服务端有坑
- `home_stream_data.stream_url` 可能为空，频道用模块 `id` 拉列表
- `daily/` stage 可能为空数组
- 部分片源 `outside/links` 返回空列表，由员工2 做空态
