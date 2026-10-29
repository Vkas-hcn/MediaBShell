# Employee-2 展示层 WIP（等 e1 中间包再合打）

> 2026-10-27 Asia/Shanghai · 真机停 · 禁点 CTA · 未打最终 APK

## 已改文件（仅展示层 + 进页 preload hook）

| 文件 | 改动 |
|---|---|
| `ads/AdActivity.kt` | `applyCloseArea` 按 **dp**；`AdPolicy.resolveCloseAreaDp()` floor≥24；热区 `ad_close_area`；禁 CTA |
| `res/layout/activity_ad_native.xml` | 全屏 Native + invisible `@id/ad_close_area`（默认 gone，代码按 dp 显式 sizing） |
| `ui/detail/DetailActivity.kt` | `onCreate`/`onStart` → `preload(NORMAL_1/2)` **不 show**；保留 `BackHandler`→`return_ad` |
| `ui/nav/AppNav.kt` | 进 Search tab → preload normal_1/2；离 tab → 既有 `show(RETURN_AD)` 保留 |

## 明确未改（归员工1）

- `AdScheduler.kt` / `AdGate` / `AdSceneRepository` / `SceneController` / `AdPolicy`（只**调用** resolve）
- `hd_ad_config.json` / `hd_load_scenes.json` / `AdSceneModels` 默认
- splash_cold/hot / notify_aoa Show 路径（notify_aoa = 分析标签，非独立 Show 位）
- 未造独立 notify AdActivity 入口

## 编译

- `./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL**
- **未** assemble / **未**拷 `_deliver/app-debug-e2-ads-replicate.apk`（等 e1 中间包）

## Player

- 既有 `attemptReturnAdThenFinish` + BackHandler 保留；本轮未改 Player
