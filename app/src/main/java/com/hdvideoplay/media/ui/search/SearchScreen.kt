package com.hdvideoplay.media.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.ui.components.EmptyBox
import com.hdvideoplay.media.ui.components.ErrorBox
import com.hdvideoplay.media.ui.components.LoadingBox
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.UiState
import com.hdvideoplay.media.ui.theme.BrandGold
import com.hdvideoplay.media.ui.theme.ChipBg
import com.hdvideoplay.media.ui.theme.ChipBorder
import com.hdvideoplay.media.ui.theme.MutedText
import com.hdvideoplay.media.ui.theme.PrimaryText
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayFontFamily

/** Search: rounded field + Genre/Release/Country chips (gold outline) + ranked 01–09 grid. */
@Composable
fun SearchScreen(
    onOpenDetail: (MediaItemDto) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory()),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val hasKeyword = ui.keyword.isNotBlank() && ui.mode == SearchUi.Mode.Search
    val fieldShape = RoundedCornerShape(22.dp)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = VideoPlayDimens.GridInset.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = ui.keyword,
                onValueChange = viewModel::onKeywordChange,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(1.dp, ChipBorder, fieldShape),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = MutedText,
                        fontFamily = VideoPlayFontFamily,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MutedText,
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submitSearch()
                        keyboard?.hide()
                    },
                ),
                shape = fieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ChipBg,
                    unfocusedContainerColor = ChipBg,
                    disabledContainerColor = ChipBg,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = BrandGold,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontFamily = VideoPlayFontFamily,
                ),
            )
        }

        if (ui.showTrendingTabs) {
            TrendingTabRow(
                category = ui.category,
                onSelect = viewModel::setCategory,
            )
        }
        if (ui.showFilterChips) {
            SearchFilterChips(
                genres = ui.genres,
                releaseYears = ui.releaseYears,
                countries = ui.countries,
                selectedGenre = ui.selectedGenre,
                selectedYear = ui.selectedYear,
                selectedCountry = ui.selectedCountry,
                yearsExpanded = ui.yearsExpanded,
                onGenre = viewModel::setGenre,
                onYear = viewModel::setYear,
                onCountry = viewModel::setCountry,
                onExpandYears = viewModel::expandYears,
            )
        }

        when (val s = ui.listState) {
            UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(
                s.message,
                onRetry = {
                    if (ui.mode == SearchUi.Mode.Search) viewModel.submitSearch()
                    else viewModel.loadTrending()
                },
            )
            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyBox()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(
                            horizontal = VideoPlayDimens.GridInset.dp,
                            vertical = 6.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                        verticalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(s.data, key = { _, it -> it.localId() }) { index, item ->
                            MediaPosterCard(
                                item = item,
                                onClick = { onOpenDetail(item) },
                                fillWidth = true,
                                titleOverlay = false,
                                maxTitleLines = 1,
                                showYear = true,
                                rank = if (!hasKeyword) index + 1 else null,
                                maxRankBadge = 9,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFilterChips(
    genres: List<String>,
    releaseYears: List<String>,
    countries: List<String>,
    selectedGenre: String?,
    selectedYear: String?,
    selectedCountry: String?,
    yearsExpanded: Boolean,
    onGenre: (String?) -> Unit,
    onYear: (String?) -> Unit,
    onCountry: (String?) -> Unit,
    onExpandYears: () -> Unit,
) {
    if (genres.isEmpty() && releaseYears.isEmpty() && countries.isEmpty()) return

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 0.dp),
    ) {
        if (genres.isNotEmpty()) {
            FilterChipRow(
                label = stringResource(R.string.filter_genre),
                options = genres,
                selected = selectedGenre,
                onSelect = onGenre,
            )
        }
        if (releaseYears.isNotEmpty()) {
            val recent = releaseYears.take(SearchViewModel.RECENT_YEAR_COUNT)
            val older = releaseYears.drop(SearchViewModel.RECENT_YEAR_COUNT)
            val yearOptions = if (yearsExpanded || older.isEmpty()) {
                releaseYears
            } else {
                recent
            }
            FilterChipRow(
                label = stringResource(R.string.filter_year),
                options = yearOptions,
                selected = selectedYear,
                onSelect = onYear,
                trailing = if (!yearsExpanded && older.isNotEmpty()) {
                    {
                        FilterChip(
                            label = stringResource(R.string.filter_year_earlier),
                            selected = false,
                            onClick = onExpandYears,
                        )
                    }
                } else {
                    null
                },
            )
        }
        if (countries.isNotEmpty()) {
            FilterChipRow(
                label = stringResource(R.string.filter_country),
                options = countries,
                selected = selectedCountry,
                onSelect = onCountry,
            )
        }
    }
}

@Composable
private fun FilterChipRow(
    label: String,
    options: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = VideoPlayDimens.GridInset.dp, end = 0.dp, top = 0.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MutedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = VideoPlayFontFamily,
            maxLines = 1,
            modifier = Modifier
                .padding(end = 8.dp)
                .widthIn(min = 52.dp, max = 72.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { opt ->
                FilterChip(
                    label = opt,
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                )
            }
            trailing?.invoke()
            Spacer(Modifier.padding(end = 10.dp))
        }
    }
}

/** Selected = gold outline; unselected = dark chip. */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bg = ChipBg
    val fg = if (selected) PrimaryText else MutedText
    val border = if (selected) BrandGold else ChipBorder
    Box(
        modifier = Modifier
            .height(28.dp)
            .widthIn(max = 140.dp)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontFamily = VideoPlayFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TrendingTabRow(
    category: Int,
    onSelect: (Int) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrendingTab(
                label = stringResource(R.string.trending_movies),
                selected = category == 1,
                onClick = { onSelect(1) },
            )
            TrendingTab(
                label = stringResource(R.string.trending_tv),
                selected = category == 2,
                onClick = { onSelect(2) },
            )
        }
    }
}

@Composable
private fun TrendingTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = if (selected) PrimaryText else MutedText,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontFamily = VideoPlayFontFamily,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(if (selected) BrandGold else Color.Transparent),
        )
    }
}
