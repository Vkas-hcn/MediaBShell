package com.hdvideoplay.media.ads

import android.app.Activity
import android.util.Log

/**
 * Employee2 present helpers for return / long-dwell page leave.
 *
 * V2 成对 (高靖): interstitial-feel / dwell≥60s → fullscreen shell THEN midcard
 * via [AdLogicV2.emitPairedInterstitialFeel]. Do not launch midcard from UI alone
 * on those paths.
 *
 * V2B (高靖): dwell &lt; 60s pure return → **no ad** (not midcard-only, not paired).
 *
 * V2C (高靖): same leave emits **once** (dwell vs firstWatch, **dwell preferred**);
 * empty inventory / retry lives in [AdLogicV2] (`dwell_fire_empty` + one retry).
 * **My excluded** — [PageDwellTracker.isDwellExempt] / [AdLogicV2.isMyRouteKey].
 * Short return &lt;60s still NONE. No emitFirstReturnMidcard.
 */
object NativeReturnPresenter {

    private const val TAG = "NativeReturn"

    /**
     * Return / back path.
     * - [longDwell] true → 成对 fullscreen → midcard (DWELL_BACK)
     * - false → no show (caller finishes / navigates away)
     *
     * @return true if an ad presentation was started (or empty-retry scheduled); false if short dwell.
     */
    fun presentReturn(
        activity: Activity,
        longDwell: Boolean,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean {
        if (longDwell) {
            Log.i(TAG, "presentReturn longDwell → 成对 fullscreen+midcard")
            // Also consume firstReturn so it does not double-fire later.
            if (AdLogicV2.isFirstReturnPending()) AdLogicV2.consumeFirstReturn()
            AdLogicV2.claimDwellLeaveSlot(AdLogicV2.PairReason.DWELL_BACK)
            return AdLogicV2.emitPairedInterstitialFeel(
                activity = activity,
                reason = AdLogicV2.PairReason.DWELL_BACK,
                onComplete = onDismiss,
            )
        }
        // V2B: short dwell pure return → no ad (NONE).
        Log.i(TAG, "presentReturn short dwell → no ad")
        return false
    }

    /**
     * Interstitial-feel 成对 (fullscreen → midcard). Prefer this over stage-1-only.
     * Uses ROUTE_DWELL so leave-slot mutex + empty retry apply.
     */
    fun presentFullscreenShell(
        activity: Activity,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean {
        Log.i(TAG, "presentFullscreenShell → 成对 (ROUTE_DWELL)")
        AdLogicV2.claimDwellLeaveSlot(AdLogicV2.PairReason.ROUTE_DWELL)
        return AdLogicV2.emitDwellPaired(activity, onDismiss)
    }

    /**
     * Unified leave handler (Tab / open Player / Module / Activity finish).
     * - My / exempt → no ad
     * - longDwell → 成对 (dwell preferred over firstWatch via [AdLogicV2.claimDwellLeaveSlot])
     * - short dwell → no ad (V2B)
     *
     * @return true if a paired emit was started (or empty-retry scheduled).
     */
    fun handleLeave(
        activity: Activity?,
        leave: PageDwellTracker.Leave?,
        preferDwellOverFirstWatch: Boolean = true,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean {
        if (leave == null) return false
        if (PageDwellTracker.isDwellExempt(leave.page) || AdLogicV2.isMyRouteKey(leave.page)) {
            Log.d(TAG, "handleLeave skip exempt/My page=${leave.page}")
            return false
        }
        val act = activity
        if (act == null || act.isFinishing || act.isDestroyed) {
            Log.w(TAG, "handleLeave no live activity page=${leave.page}")
            return false
        }
        if (!leave.longDwell) {
            Log.d(TAG, "handleLeave ${leave.page} dwell=${leave.dwellMs}ms <60s — no ad")
            AdLogger.returnNoAd(leave.page, leave.dwellMs)
            return false
        }
        Log.i(
            TAG,
            "handleLeave ${leave.page} dwell=${leave.dwellMs}ms ≥60s → dwell pair " +
                "(preferDwell=$preferDwellOverFirstWatch)",
        )
        AdLogger.returnPaired(leave.page, leave.dwellMs)
        // Same leave: dwell wins — claim slot so Player onEnterPlayer skips firstWatch.
        if (preferDwellOverFirstWatch) {
            AdLogicV2.claimDwellLeaveSlot(AdLogicV2.PairReason.ROUTE_DWELL)
        }
        if (AdLogicV2.isFirstReturnPending()) AdLogicV2.consumeFirstReturn()
        // Kernel owns empty inventory log + one retry (dwell_fire_empty).
        return AdLogicV2.emitDwellPaired(act, onDismiss)
    }
}
