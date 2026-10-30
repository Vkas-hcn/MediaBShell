package com.hdvideoplay.media.ui.recall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.hdvideoplay.media.BuildConfig
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.data.recall.MediaRecallConfigRemote
import com.hdvideoplay.media.data.recall.RecallConfig
import com.hdvideoplay.media.data.recall.RecallConfigRepository
import com.hdvideoplay.media.data.recall.SharedPrefsRecallConfigStore
import com.hdvideoplay.media.ui.detail.DetailActivity
import com.hdvideoplay.media.ui.player.PlayerActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface RecallPopupUiState {
    data object Hidden : RecallPopupUiState
    data class Visible(val downloadUrl: String) : RecallPopupUiState
}

/**
 * Recall popup config + app-start trigger.
 *
 * The popup is tied to a cold process start or a foreground return (hot
 * start), not to Compose route changes. MainActivity.onResume consumes the
 * pending start event after the splash / fullscreen ad has settled.
 */
internal object RecallPopupController {

    private const val TAG = "RecallPopup"
    private const val COLD_WAIT_MS = 3_000L
    private const val REFRESH_WAIT_MS = 3_000L
    private const val FOREGROUND_SETTLE_MS = 450L
    private const val PENDING_WAIT_MS = 900L
    private const val FOREGROUND_RECHECK_MS = 350L
    private const val WAIT_RESUMED_TIMEOUT_MS = 8_000L
    private const val WAIT_RESUMED_POLL_MS = 150L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<RecallPopupUiState>(RecallPopupUiState.Hidden)
    val state: StateFlow<RecallPopupUiState> = _state.asStateFlow()

    private var repository: RecallConfigRepository? = null
    private var coldFetch: Deferred<RecallConfig?>? = null
    private var startJob: Job? = null
    private var coldStart = true
    private var pendingStart = false
    private var firstForegroundSeen = false

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // The first foreground is the cold launch; init() already marked it.
            if (!firstForegroundSeen) {
                firstForegroundSeen = true
                return
            }
            // Do not queue a popup when the hot start happens on a content
            // screen; otherwise it would surface later as a page change.
            // Splash/Ad are allowed: MainActivity.onResume will consume the
            // event after they finish.
            val current = AdScheduler.currentActivityOr()
            if (current is PlayerActivity || current is DetailActivity) {
                Log.d(TAG, "foreground start ignored — current=${current.javaClass.simpleName}")
                return
            }
            pendingStart = true
            Log.i(TAG, "process foreground — recall start pending current=${current?.javaClass?.simpleName}")
            // ON_START may be dispatched a beat after MainActivity.onResume.
            // Re-check once MainActivity is actually the current activity.
            handler.postDelayed({
                if (!pendingStart) return@postDelayed
                val resumed = AdScheduler.currentActivityOr()
                if (resumed is MainActivity) {
                    Log.i(TAG, "foreground recheck — MainActivity is current")
                    onAppForeground(resumed)
                }
            }, FOREGROUND_RECHECK_MS)
        }
    }

    fun init(context: Context) {
        if (repository != null) return
        val app = context.applicationContext
        val repo = RecallConfigRepository(
            remote = MediaRecallConfigRemote(),
            store = SharedPrefsRecallConfigStore(app),
            key = BuildConfig.APPLICATION_ID,
        )
        repository = repo
        pendingStart = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        coldFetch = scope.async {
            Log.i(TAG, "cold prefetch key=${BuildConfig.APPLICATION_ID}")
            repo.refresh(force = true)
        }
    }

    /**
     * Call from MainActivity.onResume. This is intentionally NOT called from a
     * Home route effect: tab switches and returning from Detail/Player must not
     * re-show the popup. Only cold/hot app-start events set pendingStart.
     */
    fun onAppForeground(activity: ComponentActivity) {
        val repo = repository ?: return
        Log.i(
            TAG,
            "host resumed pending=$pendingStart active=${startJob?.isActive} cold=$coldStart",
        )
        // A new start event supersedes a pending wait. Do not cancel an
        // in-flight config evaluation (pending already consumed).
        if (pendingStart && startJob?.isActive == true) {
            startJob?.cancel()
        }

        startJob = scope.launch {
            // In some hot-start sequences ProcessLifecycle ON_START arrives
            // just after MainActivity.onResume. Give the pending marker a beat
            // instead of dropping the popup.
            var waited = 0L
            while (!pendingStart && waited < PENDING_WAIT_MS) {
                delay(50L)
                waited += 50L
            }
            if (!pendingStart) return@launch

            // Let HotStartManager launch the hot splash or App Open ad first.
            // MainActivity is paused while either surface is on top; wait until
            // it is RESUMED again instead of flashing the popup behind them.
            delay(FOREGROUND_SETTLE_MS)
            if (!pendingStart) return@launch
            if (!waitForResumed(activity)) return@launch
            if (!pendingStart) return@launch

            pendingStart = false
            val cold = coldStart
            coldStart = false
            evaluateStart(repo, cold)
        }
    }

    private suspend fun waitForResumed(activity: ComponentActivity): Boolean {
        val deadline = System.currentTimeMillis() + WAIT_RESUMED_TIMEOUT_MS
        while (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if (activity.isFinishing || activity.isDestroyed) return false
            if (System.currentTimeMillis() >= deadline) {
                Log.i(TAG, "start event still waiting — activity not resumed")
                return false
            }
            delay(WAIT_RESUMED_POLL_MS)
        }
        return true
    }

    fun dismiss() {
        _state.value = RecallPopupUiState.Hidden
    }

    /** Opens the backend-provided value as-is; no URL rewriting. */
    fun openDownload(context: Context, url: String) {
        val raw = url.trim()
        if (raw.isEmpty()) return
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return
        if (uri.scheme.isNullOrBlank()) {
            Log.w(TAG, "download value has no scheme; not opening")
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (t: Throwable) {
            Log.e(TAG, "cannot open download url", t)
        }
    }

    private suspend fun evaluateStart(repo: RecallConfigRepository, cold: Boolean) {
        try {
            if (cold) {
                val fetched = withTimeoutOrNull(COLD_WAIT_MS) { coldFetch?.await() }
                    ?: repo.cached()
                publish(fetched, cold = true)
            } else {
                val cached = repo.cached()
                if (cached != null) {
                    publish(cached, cold = false)
                    if (repo.isExpired()) {
                        // Refresh for the next start only; never hide/re-show
                        // the currently visible popup.
                        scope.launch {
                            val refreshed = repo.refresh(force = false)
                            Log.i(TAG, "background refresh done enabled=${refreshed?.enabled}")
                        }
                    }
                } else {
                    val fetched = withTimeoutOrNull(REFRESH_WAIT_MS) {
                        repo.refresh(force = false)
                    }
                    publish(fetched, cold = false)
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Log.e(TAG, "start evaluation failed", t)
            publish(repo.cached(), cold = cold)
        }
    }

    private fun publish(config: RecallConfig?, cold: Boolean) {
        val next = config
            ?.takeIf { it.enabled }
            ?.let { RecallPopupUiState.Visible(it.downloadUrl) }
            ?: RecallPopupUiState.Hidden
        if (_state.value != next) _state.value = next
        Log.i(
            TAG,
            "start display cold=$cold enabled=${config?.enabled == true} " +
                "ageMs=${config?.let { System.currentTimeMillis() - it.fetchedAtMs }}",
        )
    }
}
