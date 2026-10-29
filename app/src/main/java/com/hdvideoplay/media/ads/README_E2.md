# Ads package — Ad Logic V2 (e1 kernel) + e2 present

## Employee1 (V2 scheduling kernel)
| Class / API | Role |
|---|---|
| `AdLogicV2` | Cold 10s gate, firstWatch/firstReturn latches, N×10min progress, **成对** emit |
| `AdScheduler` + `AdBus` | Load≠Show; `showInternal(bypassInterval, pairSegment)`; hot must-show |
| `AdLocation.FULLSCREEN_SHELL` | Stage-1 interstitial-feel native shell (pool=normal_1+2) |
| `AdLocation.RETURN_AD` | Midcard (stage-2 of 成对, or pure short return) |
| `SceneController.loadIgnoringColdProbe` | Hot must-show refill when cold Ready would skip preload |
| `AppOpenAdLoader` | Cold/hot splash only (standalone; **not** 成对) |

### 成对（强制）
`AdLogicV2.emitPairedInterstitialFeel` → fullscreen_shell → dismiss → immediate return_ad midcard.  
Hot `splash_hot` is **not** paired.

## Employee2 (present / dwell — do not fight e1 pair enqueue)
| Class | Role |
|---|---|
| `AdActivity` + midcard/fullscreen XML | Present mode from `NativePresentMode` |
| `NativePresentHints` / `NativeReturnPresenter` | Set mode; longDwell → call 成对 API |
| `PageDwellTracker` | ≥60s dwell tracking for routes |
| Detail/Search/Player leave | Prefer `NativeReturnPresenter.presentReturn(longDwell)` |

## Discipline
Acceptance = show / waterfall / dismiss only. **Never click ad CTAs.**  
V2 paths never use InterstitialAd API.
