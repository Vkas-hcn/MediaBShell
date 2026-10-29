package com.hdvideoplay.media.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.MobileAds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scene-driven Load/Show bus + Ad Logic V2 kernel hooks.
 *
 * **Load ≠ Show**
 * - [load] / [preload] = SceneController.load() only (fill cache)
 * - [emit] / [show] = AdBus location → pick lowest-priority Ready → present
 *
 * Locations: first_splash_cold / splash_cold / splash_hot / return_ad / fullscreen_shell
 *
 * V2 (高靖):
 * - Hot resume MUST attempt Show splash_hot (preload skip must not swallow Show)
 * - Cold 10s quiet forbids business emits (splash excluded)
 * - Interstitial-feel paths use [AdLogicV2.emitPairedInterstitialFeel] (成对)
 * - V2 paths never call InterstitialAd API (NATIVE / App Open only)
 *
 * Acceptance = show / waterfall / dismiss only. NEVER auto-click ad CTAs.
 */
object AdScheduler : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private const val TAG = "AdScheduler"
    private const val PREFS = "ad_scheduler_prefs"
    private const val KEY_LAST_COLD_DAY = "last_cold_splash_day"

    private lateinit var app: Application
    private lateinit var repo: AdSceneRepository
    private lateinit var prefs: SharedPreferences

    private val loaders = ConcurrentHashMap<AdFormat, AdFormatLoader>()
    private val controllers = ConcurrentHashMap<String, SceneController>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sdkReady = AtomicBoolean(false)
    private val showing = AtomicBoolean(false)

    @Volatile private var currentActivity: Activity? = null
    @Volatile private var appInForeground = true
    @Volatile private var wasBackgrounded = false
    @Volatile private var lastShowAtMs = 0L
    @Volatile private var coldSceneName: String = AdScenes.COLD_OPEN_A
    @Volatile private var initialized = false
    /** Suppress one warm App Open when cold splash itself just finished (avoid double). */
    @Volatile private var suppressNextHotShow = false

    /** Optional: employee2 may replace / add loaders after [init]. */
    fun registerLoader(loader: AdFormatLoader) {
        loaders[loader.format] = loader
        Log.i(TAG, "Registered loader format=${loader.format.jsonKey}")
    }

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        repo = AdSceneRepository(application).also { it.loadFromAssets() }

        // V2 B: record cold-start t0 as early as process start.
        AdLogicV2.markColdStart()

        registerLoader(AppOpenAdLoader())
        registerLoader(NativeAdLoader())
        // Loader retained for capability / remote JSON; V2 show paths never pick INTERSTITIAL.
        registerLoader(InterstitialAdLoader())

        ensureControllers()

        application.registerActivityLifecycleCallbacks(this)
        mainHandler.post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        MobileAds.initialize(application) {
            sdkReady.set(true)
            Log.i(TAG, "MobileAds initialized")
        }
    }

    private fun ensureControllers() {
        fun make(name: String, coldProbe: (() -> Boolean)? = null) =
            SceneController(
                sceneName = name,
                appContext = { app },
                repo = { if (::repo.isInitialized) repo else null },
                loaders = { loaders },
                coldReadyProbe = coldProbe,
            )
        controllers[AdScenes.COLD_OPEN_A] = make(AdScenes.COLD_OPEN_A)
        controllers[AdScenes.COLD_OPEN] = make(AdScenes.COLD_OPEN)
        controllers[AdScenes.HOT_OPEN] = make(AdScenes.HOT_OPEN) {
            // Hot skips warm preload while any cold pool still has Ready (§B.3 / ul2).
            // V2 A: Show splash_hot is NOT gated by this — only load() skips.
            controller(AdScenes.COLD_OPEN_A).hasReady() ||
                controller(AdScenes.COLD_OPEN).hasReady()
        }
        controllers[AdScenes.NORMAL_1] = make(AdScenes.NORMAL_1)
        controllers[AdScenes.NORMAL_2] = make(AdScenes.NORMAL_2)
    }

    private fun controller(scene: String): SceneController =
        controllers.getOrPut(scene) {
            SceneController(
                sceneName = scene,
                appContext = { app },
                repo = { if (::repo.isInitialized) repo else null },
                loaders = { loaders },
                coldReadyProbe = if (scene == AdScenes.HOT_OPEN) {
                    {
                        controller(AdScenes.COLD_OPEN_A).hasReady() ||
                            controller(AdScenes.COLD_OPEN).hasReady()
                    }
                } else null,
            )
        }

    fun config(): HdAdConfig = if (::repo.isInitialized) repo.config else HdAdConfig()

    fun sceneRepo(): AdSceneRepository? = if (::repo.isInitialized) repo else null

    fun setColdScene(scene: String) {
        coldSceneName = scene
        Log.i(TAG, "Cold scene → $scene (A/B via AdGate / jb3.x)")
    }

    fun coldScene(): String = coldSceneName

    fun currentActivityOr(fallback: Activity? = null): Activity? {
        val cur = currentActivity
        if (cur != null && !cur.isFinishing && !cur.isDestroyed) return cur
        return fallback?.takeUnless { it.isFinishing || it.isDestroyed }
    }

    // --- Load API (装弹) ---

    fun load(scene: String) = preload(scene)

    fun preload(scene: String) {
        if (!::repo.isInitialized) {
            Log.w(TAG, "preload($scene) before init")
            return
        }
        controller(scene).load()
    }

    /**
     * Force hot_open load even when cold Ready (V2 A retry path).
     * Does not change the default skip rule for ordinary preload.
     */
    fun forcePreloadHot() {
        if (!::repo.isInitialized) return
        val c = controller(AdScenes.HOT_OPEN)
        if (c.hasReady()) {
            Log.d(TAG, "forcePreloadHot skip — hot already Ready")
            return
        }
        Log.i(TAG, "forcePreloadHot (bypass cold Ready skip)")
        c.loadIgnoringColdProbe()
    }

    // --- Show API (扣扳机) ---

    fun emit(
        location: AdLocation,
        activity: Activity? = currentActivity,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean = show(location, activity, onDismiss)

    @JvmOverloads
    fun show(
        location: AdLocation,
        activity: Activity? = currentActivity,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean = showInternal(
        location = location,
        activity = activity,
        bypassInterval = location == AdLocation.FIRST_SPLASH_COLD,
        pairSegment = false,
        preferredFormat = preferredFormatFor(location),
        onDismiss = onDismiss,
    )

    /**
     * Internal show used by V2 成对 chain and gates.
     *
     * @param bypassInterval skip hd_ad_interval (pair mid segment / must-show splash)
     * @param pairSegment when true, allow re-entry right after prior segment released showing
     * @param preferredFormat if set, only pick this format (V2: NATIVE for shell/midcard)
     */
    fun showInternal(
        location: AdLocation,
        activity: Activity? = currentActivity,
        bypassInterval: Boolean = false,
        pairSegment: Boolean = false,
        preferredFormat: AdFormat? = null,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean {
        val act = activity
        if (act == null || act.isFinishing || act.isDestroyed) {
            Log.w(TAG, "show(${location.jsonKey}) no live activity")
            onDismiss(false)
            return false
        }

        // V2 B: cold 10s quiet — forbid business emits (splash excluded).
        if (AdLogicV2.isBusinessEmitBlocked(location)) {
            Log.i(TAG, "show(${location.jsonKey}) blocked by cold quiet 10s")
            onDismiss(false)
            return false
        }

        if (!showing.compareAndSet(false, true)) {
            Log.d(TAG, "show(${location.jsonKey}) already showing pairSegment=$pairSegment")
            onDismiss(false)
            return false
        }
        if (!bypassInterval && !passesInterval(location)) {
            showing.set(false)
            Log.d(TAG, "show(${location.jsonKey}) blocked by hd_ad_interval")
            onDismiss(false)
            return false
        }

        val pick = pickReady(location, preferredFormat)
        if (pick == null) {
            showing.set(false)
            Log.i(TAG, "show(${location.jsonKey}) Empty — load≠show, not blocking")
            kickMissPreload(location)
            onDismiss(false)
            return false
        }
        val (scene, entry) = pick

        // V2 E: never present InterstitialAd on V2 business / shell paths.
        if (isV2NativePath(location) && entry.format == AdFormat.INTERSTITIAL) {
            showing.set(false)
            Log.w(TAG, "show(${location.jsonKey}) refused INTERSTITIAL on V2 path")
            onDismiss(false)
            return false
        }

        val loader = loaders[entry.format]
        if (loader == null) {
            showing.set(false)
            Log.w(TAG, "show: no loader for ${entry.format}")
            onDismiss(false)
            return false
        }

        Log.i(
            TAG,
            "show(${location.jsonKey}) scene=$scene P${entry.node.priority} " +
                "fmt=${entry.format.jsonKey} unit=${entry.node.unitId} pair=$pairSegment",
        )
        val started = loader.show(act, entry, location) { shown ->
            controller(scene).consume(entry)
            lastShowAtMs = System.currentTimeMillis()
            if (location == AdLocation.FIRST_SPLASH_COLD || location == AdLocation.SPLASH_COLD) {
                markColdSplashShownToday()
            }
            showing.set(false)
            onDismiss(shown)
        }
        if (!started) {
            showing.set(false)
        }
        return started
    }

    private fun isV2NativePath(location: AdLocation): Boolean =
        location == AdLocation.FULLSCREEN_SHELL || location == AdLocation.RETURN_AD

    private fun preferredFormatFor(location: AdLocation): AdFormat? =
        when (location) {
            AdLocation.FULLSCREEN_SHELL, AdLocation.RETURN_AD -> AdFormat.NATIVE
            else -> null
        }

    private fun pickReady(
        location: AdLocation,
        preferredFormat: AdFormat? = null,
    ): Pair<String, CachedAd>? {
        val scenes = scenesForLocation(location)
        var best: Pair<String, CachedAd>? = null
        for (scene in scenes) {
            val entry = controller(scene).peekLowestPriority(preferredFormat) ?: continue
            // Hard-exclude interstitial from V2 native paths even if preferredFormat null.
            if (isV2NativePath(location) && entry.format == AdFormat.INTERSTITIAL) continue
            if (best == null || entry.node.priority < best.second.node.priority) {
                best = scene to entry
            }
        }
        return best
    }

    private fun scenesForLocation(location: AdLocation): List<String> = when (location) {
        AdLocation.FIRST_SPLASH_COLD,
        AdLocation.SPLASH_COLD,
        -> listOf(coldSceneName)

        AdLocation.SPLASH_HOT -> {
            // Hot resume must use the hot App Open slot; never fall back to cold.
            listOf(AdScenes.HOT_OPEN)
        }

        AdLocation.RETURN_AD,
        AdLocation.FULLSCREEN_SHELL,
        -> {
            // Normal1+Normal2 merge; Hot/Cold do not participate
            listOf(AdScenes.NORMAL_1, AdScenes.NORMAL_2)
        }
    }

    private fun kickMissPreload(location: AdLocation) {
        when (location) {
            AdLocation.FIRST_SPLASH_COLD,
            AdLocation.SPLASH_COLD,
            -> preload(coldSceneName)

            AdLocation.SPLASH_HOT -> {
                // V2 A: miss must still try to fill hot even if cold Ready would skip.
                forcePreloadHot()
            }

            AdLocation.RETURN_AD,
            AdLocation.FULLSCREEN_SHELL,
            -> {
                preload(AdScenes.NORMAL_1)
                preload(AdScenes.NORMAL_2)
            }
        }
    }

    fun isReady(scene: String): Boolean = controller(scene).hasReady()

    fun isReadyFor(location: AdLocation): Boolean =
        pickReady(location, preferredFormatFor(location)) != null

    fun hasColdOrHotReady(): Boolean =
        isReady(AdScenes.COLD_OPEN_A) ||
            isReady(AdScenes.COLD_OPEN) ||
            isReady(AdScenes.HOT_OPEN)

    fun coldSplashLocation(): AdLocation =
        if (wasColdSplashShownToday()) AdLocation.SPLASH_COLD else AdLocation.FIRST_SPLASH_COLD

    private fun dayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun wasColdSplashShownToday(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getString(KEY_LAST_COLD_DAY, null) == dayKey()
    }

    private fun markColdSplashShownToday() {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(KEY_LAST_COLD_DAY, dayKey()).apply()
    }

    private fun passesInterval(location: AdLocation): Boolean {
        // First cold splash of the day always allowed; hot resume obeys hd_ad_interval.
        if (location == AdLocation.FIRST_SPLASH_COLD) return true
        val intervalMs = AdPolicy.adIntervalMs()
        if (intervalMs <= 0) return true
        return System.currentTimeMillis() - lastShowAtMs >= intervalMs
    }

    // --- Warm start (App Open) — V2 A: MUST Show splash_hot ---

    override fun onStart(owner: LifecycleOwner) {
        appInForeground = true
        if (wasBackgrounded) {
            wasBackgrounded = false
            if (suppressNextHotShow) {
                suppressNextHotShow = false
                Log.d(TAG, "warm resume: suppressNextHotShow consumed")
                return
            }
            val act = currentActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                attemptHotSplashMustShow(act)
            }
        }
    }

    /**
     * V2 A: warm resume always attempts Show splash_hot.
     * Preload may still skip when cold Ready, but Show uses any Ready open inventory;
     * if Empty, forcePreloadHot and retry once — never silently swallow Show.
     */
    private fun attemptHotSplashMustShow(act: Activity) {
        Log.i(TAG, "V2 hot resume: attempt splash_hot App Open")
        // Hot must ignore the cold-ready skip: hot_open owns the warm App Open slot.
        forcePreloadHot()
        mainHandler.postDelayed({
            val live = currentActivityOr(act) ?: return@postDelayed
            if (showing.get()) {
                // Retry shortly — do not drop the must-show permanently.
                Log.d(TAG, "hot must-show deferred — already showing")
                mainHandler.postDelayed({ attemptHotSplashShowOnce(live, retried = true) }, 600L)
                return@postDelayed
            }
            attemptHotSplashShowOnce(live, retried = false)
        }, 400L)
    }

    private fun attemptHotSplashShowOnce(act: Activity, retried: Boolean) {
        val started = showInternal(
            location = AdLocation.SPLASH_HOT,
            activity = act,
            bypassInterval = false,
            pairSegment = false,
            preferredFormat = AdFormat.LAUNCH_SCREEN,
            onDismiss = { shown ->
                Log.i(TAG, "hot splash_hot dismiss shown=$shown (standalone App Open, not 成对)")
            },
        )
        if (started) return
        if (retried) {
            Log.w(TAG, "hot must-show: still Empty after retry")
            return
        }
        // Empty — force hot load bypassing cold Ready skip, then retry Show once.
        Log.i(TAG, "hot must-show Empty → forcePreloadHot + retry")
        forcePreloadHot()
        mainHandler.postDelayed({
            val live = currentActivityOr(act) ?: return@postDelayed
            if (showing.get()) return@postDelayed
            attemptHotSplashShowOnce(live, retried = true)
        }, 1200L)
    }

    override fun onStop(owner: LifecycleOwner) {
        appInForeground = false
        wasBackgrounded = true
    }

    // --- Activity tracking ---

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }
}

/**
 * AdBus facade (HaloDrift no3.b). Emit location → ShowRouter.
 * Prefer [AdScheduler.emit] / [AdScheduler.show]; this object is the §B name.
 *
 * For interstitial-feel 成对, use [AdLogicV2.emitPairedInterstitialFeel].
 */
object AdBus {
    fun emit(
        location: AdLocation,
        activity: Activity? = null,
        onDismiss: (shown: Boolean) -> Unit = {},
    ): Boolean = AdScheduler.emit(location, activity, onDismiss)

    /** V2: 成对 fullscreen shell → midcard. */
    fun emitPaired(
        activity: Activity?,
        reason: AdLogicV2.PairReason = AdLogicV2.PairReason.INTERSTITIAL_FEEL,
        onComplete: (shownPair: Boolean) -> Unit = {},
    ): Boolean = AdLogicV2.emitPairedInterstitialFeel(activity, reason, onComplete)
}
