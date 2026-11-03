package com.hdvideoplay.media.ui.nav

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hdvideoplay.media.MainActivity
import com.hdvideoplay.media.R
import com.hdvideoplay.media.ads.AdScenes
import com.hdvideoplay.media.ads.AdScheduler
import com.hdvideoplay.media.ads.NativeReturnPresenter
import com.hdvideoplay.media.ads.PageDwellTracker
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.local.WatchHistoryRepository
import com.hdvideoplay.media.track.TrackEvents
import com.hdvideoplay.media.ui.channel.ChannelScreen
import com.hdvideoplay.media.ui.home.HomeScreen
import com.hdvideoplay.media.ui.module.ModuleScreen
import com.hdvideoplay.media.ui.my.AboutScreen
import com.hdvideoplay.media.ui.my.DownloadsScreen
import com.hdvideoplay.media.ui.my.FavoritesScreen
import com.hdvideoplay.media.ui.my.HistoryScreen
import com.hdvideoplay.media.ui.my.MyScreen
import com.hdvideoplay.media.ui.my.SettingsScreen
import com.hdvideoplay.media.ui.player.PlayerActivity
import com.hdvideoplay.media.ui.recall.RecallPopup
import com.hdvideoplay.media.ui.recall.RecallPopupController
import com.hdvideoplay.media.ui.recall.RecallPopupUiState
import com.hdvideoplay.media.ui.search.SearchScreen
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.MutedText
import com.hdvideoplay.media.ui.theme.TabBarBg
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily

object Routes {
    const val HOME = "home"
    const val CHANNEL = "channel"
    const val SEARCH = "search"
    const val MY = "my"
    const val HISTORY = "history"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val MODULE = "module/{moduleId}/{title}"

    fun module(moduleId: Int, title: String): String =
        "module/$moduleId/${Uri.encode(title)}"
}


/** Map Nav route → PageDwellTracker page key. MODULE uses stable key (optional moduleId). */
private fun routeToDwellPage(
    route: String?,
    backStack: androidx.navigation.NavBackStackEntry?,
): String? {
    if (route == null) return null
    // My excluded (高靖 V2C): no dwell enter / no leave pair from My.
    return when {
        route == Routes.HOME -> PageDwellTracker.HOME
        route == Routes.CHANNEL -> PageDwellTracker.CHANNEL
        route == Routes.SEARCH -> PageDwellTracker.SEARCH
        route == Routes.MY -> null
        route == Routes.HISTORY -> PageDwellTracker.HISTORY
        route.startsWith("module/") || route == Routes.MODULE || route.startsWith("module") -> {
            val moduleId = backStack?.arguments?.getInt("moduleId")
            PageDwellTracker.moduleKey(moduleId)
        }
        else -> null
    }
}


private data class Tab(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
    val selectedIconRes: Int,
)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val historyRepository = remember(context) { WatchHistoryRepository.get(context) }
    val continueWatching by historyRepository.observeContinueWatching(1)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recallState by RecallPopupController.state.collectAsStateWithLifecycle()
    var dismissedResumeKey by rememberSaveable { mutableStateOf<String?>(null) }

    // V2C: Home/Channel/Search/History/Module enter dwell (+ Detail/Player Activities).
    // My excluded: clear previous clock without firing; never enter(MY).
    // ≥60s leave → 成对; <60s → no ad (V2B).
    LaunchedEffect(currentRoute) {
        val act = context as? Activity
        when {
            currentRoute == Routes.MY -> {
                // Enter My: drop previous dwell clock; do NOT fire longDwell pair.
                val cleared = PageDwellTracker.leaveCurrent("route:my")
                Log.i(
                    "AppNav",
                    "enter My — clear dwell without fire " +
                        "(prev=${cleared?.page} dwell=${cleared?.dwellMs})",
                )
            }
            else -> {
                val page = routeToDwellPage(currentRoute, backStack)
                if (page != null) {
                    // Dwell pages (incl. Home): preload normal_1/2 on enter.
                    Log.i("AppNav", "enter $page → preload normal_1 + normal_2 (no show)")
                    AdScheduler.preload(AdScenes.NORMAL_1)
                    AdScheduler.preload(AdScenes.NORMAL_2)
                    val leave = PageDwellTracker.enter(page)
                    // From My: leave is null (never entered) → no emit for leaving My.
                    NativeReturnPresenter.handleLeave(act, leave)
                } else if (currentRoute != null) {
                    val leave = PageDwellTracker.leaveCurrent("route:$currentRoute")
                    NativeReturnPresenter.handleLeave(act, leave)
                }
            }
        }
    }

    // Lanhu slices: selected = ic_nav_*_sle (gold fill); unselected = ic_nav_*_nor.
    val tabs = listOf(
        Tab(Routes.HOME, R.string.nav_home, R.drawable.ic_nav_home_nor, R.drawable.ic_nav_home_sle),
        Tab(Routes.CHANNEL, R.string.nav_channel, R.drawable.ic_nav_channel_nor, R.drawable.ic_nav_channel_sle),
        Tab(Routes.SEARCH, R.string.nav_search, R.drawable.ic_nav_search_nor, R.drawable.ic_nav_search_sle),
        Tab(Routes.MY, R.string.nav_my, R.drawable.ic_nav_my_nor, R.drawable.ic_nav_my_sle),
    )
    val showBottomBar = currentRoute in tabs.map { it.route }

    // Re-bind dwell when Main resumes after Player/Detail (route unchanged → LaunchedEffect skips).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(currentRoute, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val page = routeToDwellPage(currentRoute, backStack)
                if (page != null && PageDwellTracker.currentPage() == null) {
                    AdScheduler.preload(AdScenes.NORMAL_1)
                    AdScheduler.preload(AdScenes.NORMAL_2)
                    PageDwellTracker.enter(page)
                    Log.d("AppNav", "resume re-bind dwell page=$page")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }


    /**
     * 内容入口统一落 PlayerActivity（方案 A 现状不变）；from_position 记录详情来源。
     */
    fun openDetail(item: MediaItemDto, fromPosition: String, moduleId: Int? = null) {
        // Play-auto brief (方案 A): content entries open PlayerActivity directly with autoPlay.
        // Prefer media_id; fall back to id (daily). Log both to catch Dragonflies dual hits.
        val id = item.localId()
        Log.i(
            "AppNav",
            "openPlayer localId=$id media_id=${item.mediaId} id=${item.id} title=${item.title.take(40)}",
        )
        if (id <= 0) return
        // Leaving any Compose dwell page for Player.
        // V2C: longDwell → 成对 once (dwell preferred; consumes firstWatch so Player skips).
        // Do not delay Player on dismiss — avoids racing stage-2 midcard.
        val leave = PageDwellTracker.leaveCurrent("openPlayer")
        NativeReturnPresenter.handleLeave(
            activity = context as? Activity,
            leave = leave,
            preferDwellOverFirstWatch = true,
        )
        context.startActivity(
            PlayerActivity.createIntent(
                context = context,
                mediaId = id,
                episode = 0,
                title = item.title,
                startFullscreen = false,
                lineIndex = 0,
                fromPosition = fromPosition,
                moduleId = moduleId,
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                // Keep the 66dp tab bar above the system navigation area. Without this,
                // NavigationBar's own window inset eats most of the fixed height on
                // 3-button navigation and the icons/labels get clipped.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TabBarBg)
                        .navigationBarsPadding(),
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp)
                            .height(VideoPlayDimens.TabBarHeight.dp),
                        containerColor = TabBarBg,
                        contentColor = MutedText,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                    // 每次回到首页都重新判定一次通知权限（系统框能弹就弹系统框）
                                    if (tab.route == Routes.HOME) {
                                        (context as? MainActivity)?.onHomeTabSelected()
                                    }
                                },
                                icon = {
                                    Icon(
                                        painter = painterResource(
                                            if (selected) tab.selectedIconRes else tab.iconRes,
                                        ),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(VideoPlayDimens.NavIconSize.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(tab.labelRes),
                                        color = if (selected) BrandGold else MutedText,
                                        fontSize = 8.sp,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        fontFamily = VideoPlayFontFamily,
                                        maxLines = 1,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BrandGold,
                                    selectedTextColor = BrandGold,
                                    unselectedIconColor = MutedText,
                                    unselectedTextColor = MutedText,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
            ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenModule = { id, title ->
                        navController.navigate(Routes.module(id, title))
                    },
                    onOpenDetail = { item, moduleId ->
                        openDetail(item, TrackEvents.FROM_HOME_MODULE, moduleId)
                    },
                )
            }
            composable(Routes.CHANNEL) {
                ChannelScreen(
                    onOpenDetail = { item ->
                        openDetail(item, TrackEvents.FROM_CHANNEL)
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenDetail = { item ->
                        openDetail(item, TrackEvents.FROM_SEARCH)
                    },
                )
            }
            composable(Routes.MY) {
                MyScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.MODULE,
                arguments = listOf(
                    navArgument("moduleId") { type = NavType.IntType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { entry ->
                val moduleId = entry.arguments?.getInt("moduleId") ?: 0
                val title = entry.arguments?.getString("title").orEmpty()
                ModuleScreen(
                    moduleId = moduleId,
                    title = title,
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { item ->
                        openDetail(item, TrackEvents.FROM_HOME_MODULE, moduleId)
                    },
                )
            }
        }
            QuickReturnOverlay(
                progress = continueWatching.firstOrNull(),
                dismissedKey = dismissedResumeKey,
                onOpen = { progress ->
                    context.startActivity(
                        PlayerActivity.createIntent(
                            context = context,
                            mediaId = progress.mediaId,
                            episode = progress.episode ?: 0,
                            title = progress.title,
                            startFullscreen = false,
                            lineIndex = 0,
                            startPositionMs = progress.progressSec * 1000L,
                            fromPosition = TrackEvents.FROM_RESUME,
                        ),
                    )
                },
                onDismiss = { dismissedResumeKey = it },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 12.dp),
            )

            // App-start modal. The trigger lives in MainActivity.onResume and
            // only fires for cold/hot starts, never for tab or route changes.
            // It is a Dialog window so the scrim covers the system bars.
            when (val recall = recallState) {
                is RecallPopupUiState.Visible -> {
                    // 同一次弹窗展示生命周期内：点击/忽略各只上报一次；点击不误判为忽略。
                    var clickReported by remember(recall) { mutableStateOf(false) }
                    var dismissReported by remember(recall) { mutableStateOf(false) }
                    RecallPopup(
                        downloadUrl = recall.downloadUrl,
                        onDismiss = {
                            if (!dismissReported) {
                                dismissReported = true
                                TrackEvents.recallDismiss()
                            }
                            RecallPopupController.dismiss()
                        },
                        onUpgrade = { url ->
                            if (!clickReported) {
                                clickReported = true
                                TrackEvents.recallClick()
                            }
                            RecallPopupController.dismiss()
                            RecallPopupController.openDownload(context, url)
                        },
                    )
                }
                RecallPopupUiState.Hidden -> Unit
            }
        }
    }
}
