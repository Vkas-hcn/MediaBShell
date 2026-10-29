# Employee-2 交付状态 · P0 return_ad 系统返回

> 更新时间：2026-10-27 18:28:43 CST（Asia/Shanghai）
> 基线：`_deliver/app-debug-e2-sprint15-ads.apk` MD5 `a75fe8159af0546a2425f23103731cc8`
> 本轮：`_deliver/app-debug-e2-sprint15-returnad.apk` MD5 `f47ead24b7426bc5016ec2d869ec60c4`

## 编译结果

| 项 | 状态 |
|---|---|
| assembleDebug | **BUILD SUCCESSFUL**（`--rerun-tasks`） |
| 交付 APK | `_deliver/app-debug-e2-sprint15-returnad.apk` |
| APK md5 | `f47ead24b7426bc5016ec2d869ec60c4` |

## 本轮改动

- Detail / Player：系统返回 → `BackHandler` → `attemptReturnAdThenFinish()`
- Player：EpisodesSheet / LineQualityPanel 系统返回先关面板
- Search：已核对 AppNav leave-Search → RETURN_AD，无缺口
- 报告：`_deliver/RETURNAD_REPORT.md`

## 验收纪律

冷启 60s 频控 / normal 未就绪 miss = 预期。禁止点广告 CTA。
