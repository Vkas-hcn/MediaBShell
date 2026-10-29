package com.hdvideoplay.media.ui.module

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.EmptyBox
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.PageBg
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily
import kotlinx.coroutines.flow.distinctUntilChanged

/** More / Module — back + title + 3-col poster grid only (09_more.png). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleScreen(
    moduleId: Int,
    title: String,
    onBack: () -> Unit,
    onOpenDetail: (MediaItemDto) -> Unit,
    viewModel: ModuleViewModel = viewModel(
        key = "module-$moduleId",
        factory = ModuleViewModel.factory(moduleId, title),
    ),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, ui.items.size, ui.endReached, ui.loading) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 4
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) viewModel.loadMore()
            }
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        ui.title.ifBlank { title },
                        fontFamily = VideoPlayFontFamily,
                        maxLines = 1,
                        color = PrimaryText,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryText,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PageBg,
                    titleContentColor = PrimaryText,
                    navigationIconContentColor = PrimaryText,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = ui.initial) {
                UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::refresh)
                is UiState.Success -> {
                    if (ui.items.isEmpty()) {
                        EmptyBox()
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = gridState,
                                contentPadding = PaddingValues(
                                    horizontal = VideoPlayDimens.GridInset.dp,
                                    vertical = 6.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                                verticalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            ) {
                                items(ui.items, key = { "${it.localId()}-${it.title}" }) { item ->
                                    MediaPosterCard(
                                        item = item,
                                        onClick = { onOpenDetail(item) },
                                        fillWidth = true,
                                        titleOverlay = false,
                                        maxTitleLines = 1,
                                    )
                                }
                            }
                            if (ui.loading) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = BrandGold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
