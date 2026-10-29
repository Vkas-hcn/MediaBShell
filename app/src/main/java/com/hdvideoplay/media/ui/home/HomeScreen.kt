package com.hdvideoplay.media.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.HomeModuleDto
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.BrandWordmark
import com.hdvideoplay.media.ui.components.EmptyBox
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.MutedText
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily

@Composable
fun HomeScreen(
    onOpenModule: (moduleId: Int, title: String) -> Unit,
    onOpenDetail: (MediaItemDto) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::refresh)
        is UiState.Success -> {
            if (s.data.isEmpty()) {
                EmptyBox()
            } else {
                HomeContent(
                    modules = s.data,
                    onOpenModule = onOpenModule,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    modules: List<HomeModuleDto>,
    onOpenModule: (moduleId: Int, title: String) -> Unit,
    onOpenDetail: (MediaItemDto) -> Unit,
) {
    // No drawer — centered wordmark + module rails.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(VideoPlayDimens.SectionGap.dp),
    ) {
        item(key = "wordmark") {
            // Lanhu: 56dp header + 24dp before the first section title.
            BrandWordmark(modifier = Modifier.padding(bottom = 8.dp))
        }
        items(modules, key = { it.id }) { module ->
            ModuleRow(
                module = module,
                onMore = { onOpenModule(module.id, module.moduleName) },
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

@Composable
private fun ModuleRow(
    module: HomeModuleDto,
    onMore: () -> Unit,
    onOpenDetail: (MediaItemDto) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = VideoPlayDimens.GridInset.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .padding(end = 8.dp)
                    .width(VideoPlayDimens.SectionBarWidth.dp)
                    .height(VideoPlayDimens.SectionBarHeight.dp)
                    .background(BrandGold, RoundedCornerShape(2.dp)),
            )
            Text(
                text = module.moduleName,
                color = PrimaryText,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = VideoPlayFontFamily,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .height(20.dp)
                    .clickable(onClick = onMore),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.more_chevron),
                    color = MutedText,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFamily = VideoPlayFontFamily,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = VideoPlayDimens.GridInset.dp),
            horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
        ) {
            items(module.data, key = { it.localId() }) { item ->
                MediaPosterCard(
                    item = item,
                    onClick = { onOpenDetail(item) },
                    widthDp = VideoPlayDimens.PosterWidth,
                    maxTitleLines = 1,
                )
            }
        }
    }
}
