package com.hdvideoplay.media.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hdvideoplay.media.R
import com.hdvideoplay.media.data.dto.DetailInfoDto
import com.hdvideoplay.media.data.dto.EpisodeDto
import com.hdvideoplay.media.data.dto.MediaItemDto
import com.hdvideoplay.media.data.dto.OutsideLinkDto
import com.hdvideoplay.media.data.dto.RecommendGroupDto
import com.hdvideoplay.media.data.dto.SeasonDto
import com.hdvideoplay.media.ui.components.AvatarImage
import com.hdvideoplay.media.ui.components.MediaPosterCard
import com.hdvideoplay.media.ui.components.SkeletonBar
import com.hdvideoplay.media.ui.components.SkeletonCircle
import com.hdvideoplay.media.ui.components.SkeletonMotion
import com.hdvideoplay.media.ui.components.SkeletonSlot
import com.hdvideoplay.media.ui.components.SkeletonTextLines
import com.hdvideoplay.media.ui.theme.VideoPlayDimens
import com.hdvideoplay.media.ui.theme.VideoPlayTokens
import kotlin.text.CharCategory

private val CapsuleShape = RoundedCornerShape(12.dp)
private val MetaChipShape = RoundedCornerShape(12.dp)
private val QualityBadgeShape = RoundedCornerShape(4.dp)
private val UnselectedBg = Color(0xFF141824)
private val UnselectedFg = Color(0xFFCBD5E1)
private val UnselectedBorder = Color(0x1FFFFFFF)
/** Selected Play Source capsule: gold gradient + gold outline + dot (稿 播放详情). */
private val SelectedGradientStart = Color(0x33F59E0B)
private val SelectedGradientEnd = Color(0x1AD97706)
private val SelectedFg = Color(0xFFFCD34D)
/** Solid gold fill for HD badge / selected episode chip. */
private val SelectedSolidBg = VideoPlayTokens.AccentGold
private val SelectedSolidFg = Color.Black
private val SelectedBorder = Color(0xFFFFB800)
private val SelectedDot = Color(0xFFFBBF24)
private val DimFg = VideoPlayTokens.Muted

/** Fixed row heights shared by the skeleton and the real watch-meta layout. */
private val SourceRowHeight = 32.dp

@Composable
private fun titleSlotHeight(): Dp =
    with(LocalDensity.current) { 26.sp.toDp() * 2 + 2.dp }

@Composable
private fun metaRowHeight(): Dp =
    with(LocalDensity.current) { 20.sp.toDp() }

@Composable
private fun overviewSlotHeight(): Dp =
    with(LocalDensity.current) { 18.sp.toDp() * 3 + 2.dp }

/** UI model for Cast tab — stage detail API currently has no cast payload. */
data class CastMemberUi(
    val name: String,
    val role: String = "",
    val avatarUrl: String? = null,
)

/** UI model for Reviews tab — stage detail API currently has no reviews payload. */
data class ReviewUi(
    val username: String,
    val body: String,
    val stars: Float = 0f,
    val avatarUrl: String? = null,
)

private enum class WatchMetaTab {
    Overview,
    Cast,
    Reviews,
}

/**
 * Shared watch-path meta under the 16:9 player (Player portrait + Detail).
 * Lanhu Video Play: title + meta row + Overview/Cast/Reviews tabs (gold underline),
 * then Play Source / seasons / You May Also Like.
 * Gold accent #FFB800 — no Netflix red, no teal, no Material FilterChip.
 */
@Composable
fun WatchMetaSection(
    details: DetailInfoDto?,
    titleFallback: String,
    @Suppress("UNUSED_PARAMETER") currentLineLabel: String?,
    lines: List<OutsideLinkDto>,
    selectedLineIndex: Int,
    linesStatus: LinesStatus,
    lineProbeOk: List<Boolean?> = emptyList(),
    seasons: List<SeasonDto> = emptyList(),
    selectedSeasonIndex: Int = 0,
    selectedEpisode: Int = 0,
    recommends: List<RecommendGroupDto> = emptyList(),
    onSelectLine: (Int) -> Unit,
    onSelectSeason: (Int) -> Unit = {},
    onSelectEpisode: (mediaId: Int, episode: Int, title: String) -> Unit = { _, _, _ -> },
    onOpenRecommend: (MediaItemDto) -> Unit = {},
    fallbackMediaId: Int = 0,
    modifier: Modifier = Modifier,
    /** Quality capsules for the current line (Player). */
    qualityLabels: List<String> = emptyList(),
    selectedQualityIndex: Int = 0,
    onSelectQuality: (Int) -> Unit = {},
    /**
     * Raw quality string from the active play source (e.g. "720p Origin").
     * Normalized to a 4K/HD outline badge under the title; blank → hidden.
     */
    qualityBadgeRaw: String? = null,
    /** Optional rating text (e.g. "7.8") when API provides it; blank → hidden. */
    ratingText: String? = null,
    /** Optional runtime (e.g. "1h 48m"); blank → hidden. */
    runtimeText: String? = null,
    cast: List<CastMemberUi> = emptyList(),
    reviews: List<ReviewUi> = emptyList(),
    /**
     * True while detail metadata is still loading. Text slots show shimmer
     * skeletons at their final fixed sizes; real text crossfades in when false.
     */
    loading: Boolean = false,
) {
    val title = details?.title.orEmpty().ifBlank { titleFallback }.ifBlank { "Untitled" }
    val year = details?.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    val genres = details?.genres
        ?.map { it.name.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    val genreLabel = genres.joinToString(" / ").takeIf { it.isNotBlank() }
    val overview = details?.overview.orEmpty()
    val isMovie = seasons.isEmpty()
    val qualityBadge = normalizeQualityBadge(qualityBadgeRaw)
        ?: normalizeQualityBadge(qualityLabels.getOrNull(selectedQualityIndex))
    val rating = ratingText?.trim()?.takeIf { it.isNotBlank() }
    val runtime = runtimeText?.trim()?.takeIf { it.isNotBlank() }
    val metaTrail = listOfNotNull(genreLabel, year, runtime).joinToString(" · ")
        .takeIf { it.isNotBlank() }

    val titleHeight = titleSlotHeight()
    val metaHeight = metaRowHeight()
    val overviewHeight = overviewSlotHeight()
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1) Title ~20–22sp Bold white — fixed two-line slot, skeleton while loading.
        SkeletonSlot(
            loading = loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                .height(titleHeight),
            skeleton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBar(height = 18.dp, widthFraction = 0.72f)
                    SkeletonBar(height = 18.dp, widthFraction = 0.44f)
                }
            },
            content = {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )

        // 2) Meta row: fixed height and skeleton even when the API has no meta.
        Spacer(Modifier.height(8.dp))
        SkeletonSlot(
            loading = loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(metaHeight),
            alignment = Alignment.CenterStart,
            skeleton = {
                SkeletonBar(height = 12.dp, widthFraction = 0.55f)
            },
            content = {
                if (rating != null || qualityBadge != null || metaTrail != null) {
                    WatchMetaInfoRow(
                        rating = rating,
                        qualityBadge = qualityBadge,
                        metaTrail = metaTrail,
                    )
                }
            },
        )

        // 3) Tabs: Overview | Cast | Reviews — selected = AccentGold underline
        Spacer(Modifier.height(12.dp))
        WatchMetaTabs(
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(10.dp))
        WatchMetaTabContent(
            loading = loading,
            selectedTab = selectedTab,
            overview = overview,
            overviewSlotHeight = overviewHeight,
            cast = cast,
            reviews = reviews,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // 4) Play Source (custom capsules; selected AccentGold fill; no teal)
        Spacer(Modifier.height(14.dp))
        PlaySourceRow(
            lines = lines,
            selectedLineIndex = selectedLineIndex,
            linesStatus = linesStatus,
            lineProbeOk = lineProbeOk,
            onSelectLine = onSelectLine,
            qualityLabels = qualityLabels,
            selectedQualityIndex = selectedQualityIndex,
            onSelectQuality = onSelectQuality,
        )

        // 5) Season / episode chips — appended below the fixed source row only
        // after detail meta is known, so a movie never reserves a season gap.
        AnimatedVisibility(
            visible = !loading && !isMovie,
            enter = fadeIn(tween(SkeletonMotion.FadeMs)),
            exit = fadeOut(tween(SkeletonMotion.FadeMs)),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                SeasonEpisodePicker(
                    seasons = seasons,
                    selectedSeasonIndex = selectedSeasonIndex,
                    selectedEpisode = selectedEpisode,
                    fallbackMediaId = fallbackMediaId,
                    title = title,
                    onSelectSeason = onSelectSeason,
                    onSelectEpisode = onSelectEpisode,
                )
            }
        }

        // 6) You May Also Like / MORE LIKE THIS — trailing content, fade in only.
        AnimatedVisibility(
            visible = !loading && recommends.isNotEmpty(),
            enter = fadeIn(tween(SkeletonMotion.FadeMs)),
            exit = fadeOut(tween(SkeletonMotion.FadeMs)),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                recommends.forEach { group ->
                    RecommendSection(group = group, onOpen = onOpenRecommend)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        // Favorites / Download: hidden (no backend this sprint).
    }
}

/**
 * Tab content: the default Overview keeps a fixed collapsed height so the
 * Play Source row below never moves; Cast/Reviews use the same minimum height
 * and compact skeletons while detail metadata is loading.
 */
@Composable
private fun WatchMetaTabContent(
    loading: Boolean,
    selectedTab: Int,
    overview: String,
    overviewSlotHeight: Dp,
    cast: List<CastMemberUi>,
    reviews: List<ReviewUi>,
    modifier: Modifier = Modifier,
) {
    val tab = WatchMetaTab.entries[selectedTab.coerceIn(0, WatchMetaTab.entries.lastIndex)]
    when (tab) {
        WatchMetaTab.Overview -> SkeletonSlot(
            loading = loading,
            modifier = modifier.fillMaxWidth(),
            skeleton = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(overviewSlotHeight),
                ) {
                    SkeletonTextLines(
                        lineCount = 3,
                        lineHeight = 12.dp,
                        lineSpacing = 6.dp,
                        widths = listOf(1f, 1f, 0.62f),
                    )
                }
            },
            content = {
                WatchOverviewBlock(
                    overview = overview,
                    collapsedHeight = overviewSlotHeight,
                )
            },
        )
        WatchMetaTab.Cast -> SkeletonSlot(
            loading = loading,
            modifier = modifier.fillMaxWidth(),
            skeleton = { CastTabSkeleton(overviewSlotHeight) },
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = overviewSlotHeight),
                ) {
                    CastTabContent(cast = cast)
                }
            },
        )
        WatchMetaTab.Reviews -> SkeletonSlot(
            loading = loading,
            modifier = modifier.fillMaxWidth(),
            skeleton = { ReviewsTabSkeleton(overviewSlotHeight) },
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = overviewSlotHeight),
                ) {
                    ReviewsTabContent(reviews = reviews)
                }
            },
        )
    }
}

@Composable
private fun CastTabSkeleton(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonCircle(size = 28.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBar(height = 8.dp, width = 44.dp)
                }
            }
        }
    }
}

@Composable
private fun ReviewsTabSkeleton(height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonCircle(size = 36.dp)
            Column(
                modifier = Modifier.width(180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SkeletonBar(height = 10.dp, widthFraction = 0.55f)
                SkeletonBar(height = 10.dp, widthFraction = 1f)
            }
        }
    }
}

/** Map play-source quality strings to a compact 4K/HD badge; null if unknown/blank. */
fun normalizeQualityBadge(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val u = raw.uppercase()
    return when {
        "4K" in u || "2160" in u || "UHD" in u -> "4K"
        "1080" in u || "FHD" in u || "720" in u || "HD" in u || "480" in u ||
            "540" in u || "360" in u || "ORIGIN" in u -> "HD"
        else -> null
    }
}

@Composable
private fun WatchMetaInfoRow(
    rating: String?,
    qualityBadge: String?,
    metaTrail: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!rating.isNullOrBlank()) {
            Text(
                text = rating,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (!qualityBadge.isNullOrBlank()) {
            Text(
                text = qualityBadge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .background(UnselectedBg, QualityBadgeShape) // #1F1F1F
                    .border(1.dp, Color.White, QualityBadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (!metaTrail.isNullOrBlank()) {
            Text(
                text = metaTrail,
                color = VideoPlayTokens.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WatchMetaTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.tab_overview),
        stringResource(R.string.tab_cast),
        stringResource(R.string.tab_reviews),
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelect(index) }
                    .padding(bottom = 2.dp),
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.White else VideoPlayTokens.Muted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(if (selected) VideoPlayTokens.AccentGold else Color.Transparent),
                )
            }
        }
    }
}

@Composable
fun WatchTitleMetaBlock(
    title: String,
    yearRegion: String,
    currentLineLabel: String?,
    modifier: Modifier = Modifier,
) {
    // Kept for any legacy callers; Lanhu path uses WatchMetaSection title + WatchMetaInfoRow.
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (yearRegion.isNotBlank() || !currentLineLabel.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                if (yearRegion.isNotBlank()) {
                    MetaInfoCapsule(text = yearRegion, emphasized = false)
                }
                if (!currentLineLabel.isNullOrBlank()) {
                    MetaInfoCapsule(text = currentLineLabel, emphasized = true)
                }
            }
        }
    }
}

@Composable
private fun MetaInfoCapsule(
    text: String,
    emphasized: Boolean,
) {
    val bg = if (emphasized) SelectedSolidBg else UnselectedBg
    val fg = if (emphasized) SelectedSolidFg else UnselectedFg
    Text(
        text = text,
        color = fg,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(bg, MetaChipShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun WatchOverviewBlock(
    overview: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") genres: String = "",
    /**
     * Height reserved for the collapsed overview. Keeps a short synopsis from
     * shrinking the block and moving Play Source upward after load.
     */
    collapsedHeight: Dp? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (overview.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsedHeight != null) Modifier.height(collapsedHeight)
                        else Modifier,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.overview_empty),
                    color = VideoPlayTokens.Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            return
        }
        if (expanded) {
            Text(
                text = overview,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.less),
                color = VideoPlayTokens.Muted,
                fontSize = 12.sp,
                modifier = Modifier.clickable { expanded = false },
            )
        } else {
            // 2–3 lines + More (muted/white, never teal); fixed collapsed slot.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (collapsedHeight != null) Modifier.height(collapsedHeight)
                        else Modifier,
                    ),
            ) {
                Text(
                    text = overview,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp),
                )
                Text(
                    text = stringResource(R.string.more),
                    color = VideoPlayTokens.AccentGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clickable { expanded = true }
                        .padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CastTabContent(
    cast: List<CastMemberUi>,
    modifier: Modifier = Modifier,
) {
    if (cast.isEmpty()) {
        FriendlyEmptyState(
            message = stringResource(R.string.cast_empty),
            modifier = modifier,
        )
        return
    }
    val rows = cast.chunked(4)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { member ->
                    CastAvatarCell(
                        member = member,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CastAvatarCell(
    member: CastMemberUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AvatarImage(
            url = member.avatarUrl,
            name = member.name,
            size = 56.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = member.name,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (member.role.isNotBlank()) {
            Text(
                text = member.role,
                color = VideoPlayTokens.Muted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ReviewsTabContent(
    reviews: List<ReviewUi>,
    modifier: Modifier = Modifier,
) {
    if (reviews.isEmpty()) {
        FriendlyEmptyState(
            message = stringResource(R.string.reviews_empty),
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        reviews.forEach { review ->
            ReviewRow(review = review)
        }
    }
}

@Composable
private fun ReviewRow(review: ReviewUi) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarImage(
            url = review.avatarUrl,
            name = review.username,
            size = 36.dp,
            initialFontSize = 14.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = review.username,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (review.stars > 0f) {
                    Text(
                        text = starLabel(review.stars),
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
            if (review.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = review.body,
                    color = VideoPlayTokens.Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private fun starLabel(stars: Float): String {
    val full = stars.toInt().coerceIn(0, 5)
    val hasHalf = stars - full >= 0.5f && full < 5
    return buildString {
        repeat(full) { append('★') }
        if (hasHalf) append('☆')
        val remaining = 5 - full - if (hasHalf) 1 else 0
        repeat(remaining.coerceAtLeast(0)) { append('☆') }
    }
}

@Composable
private fun FriendlyEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        color = DimFg,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
fun PlaySourceRow(
    lines: List<OutsideLinkDto>,
    selectedLineIndex: Int,
    linesStatus: LinesStatus,
    lineProbeOk: List<Boolean?> = emptyList(),
    onSelectLine: (Int) -> Unit,
    qualityLabels: List<String> = emptyList(),
    selectedQualityIndex: Int = 0,
    onSelectQuality: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.play_source),
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        // One fixed 32dp row for Checking skeleton / None message / Ready chips.
        SkeletonSlot(
            loading = linesStatus == LinesStatus.Checking,
            modifier = Modifier
                .fillMaxWidth()
                .height(SourceRowHeight),
            alignment = Alignment.CenterStart,
            skeleton = {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) { index ->
                        SkeletonBar(
                            height = 32.dp,
                            width = if (index == 0) 76.dp else 66.dp,
                            shape = CapsuleShape,
                        )
                    }
                }
            },
            content = {
                when (linesStatus) {
                    LinesStatus.Checking -> Unit
                    LinesStatus.None -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SourceRowHeight)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = stringResource(R.string.no_available_lines),
                                color = VideoPlayTokens.Muted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    LinesStatus.Ready -> {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), // 稿: 12dp chip gap
                        ) {
                            lines.forEachIndexed { index, link ->
                                val selected = index == selectedLineIndex
                                val probeOk = lineProbeOk.getOrNull(index)
                                val label = link.name.ifBlank { "Line ${index + 1}" }
                                val dimmed = !selected && probeOk == false
                                SourceCapsule(
                                    label = label,
                                    selected = selected,
                                    dimmed = dimmed,
                                    onClick = { onSelectLine(index) },
                                )
                            }
                        }
                    }
                }
            },
        )
        if (linesStatus == LinesStatus.Ready && qualityLabels.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.quality),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), // 稿: 12dp chip gap
            ) {
                qualityLabels.forEachIndexed { index, label ->
                    SourceCapsule(
                        label = label,
                        selected = index == selectedQualityIndex,
                        onClick = { onSelectQuality(index) },
                    )
                }
            }
        }
    }
}

/** Custom Play Source / season / quality capsule — AccentGold fill when selected (稿07). */
@Composable
fun SourceCapsule(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val bgBrush = if (selected) {
        Brush.horizontalGradient(listOf(SelectedGradientStart, SelectedGradientEnd))
    } else {
        SolidColor(UnselectedBg)
    }
    val fg = when {
        selected -> SelectedFg
        dimmed -> UnselectedFg.copy(alpha = 0.55f)
        else -> UnselectedFg
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 160.dp)
            .height(32.dp)
            .clip(CapsuleShape)
            .background(bgBrush)
            .border(1.dp, if (selected) SelectedBorder else UnselectedBorder, CapsuleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(SelectedDot, CircleShape),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SeasonEpisodePicker(
    seasons: List<SeasonDto>,
    selectedSeasonIndex: Int,
    selectedEpisode: Int,
    fallbackMediaId: Int,
    title: String,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (mediaId: Int, episode: Int, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (seasons.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.seasons),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), // 稿: 12dp chip gap
        ) {
            seasons.forEachIndexed { index, season ->
                SourceCapsule(
                    label = season.name.ifBlank { "S${season.seasonNumber}" },
                    selected = index == selectedSeasonIndex,
                    onClick = { onSelectSeason(index) },
                )
            }
        }

        val season = seasons.getOrNull(selectedSeasonIndex) ?: return
        if (season.episodes.isEmpty()) return
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.episodes),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        val playMediaId = when {
            season.mediaId > 0 -> season.mediaId
            fallbackMediaId > 0 -> fallbackMediaId
            else -> 0
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            season.episodes.forEach { ep: EpisodeDto ->
                val selected = ep.episodeNumber == selectedEpisode
                EpisodeNumberChip(
                    number = ep.episodeNumber,
                    selected = selected,
                    onClick = {
                        onSelectEpisode(
                            playMediaId,
                            ep.episodeNumber,
                            ep.name.ifBlank { title },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeNumberChip(
    number: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val bg = if (selected) SelectedSolidBg else UnselectedBg
    val fg = if (selected) SelectedSolidFg else UnselectedFg
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(bg, shape)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, VideoPlayTokens.ChipBorder, shape),
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = number.toString(),
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun RecommendSection(
    group: RecommendGroupDto,
    onOpen: (MediaItemDto) -> Unit,
) {
    if (group.v.isEmpty()) return
    // Strip decorative emoji; white title; no teal underline.
    val rawTitle = group.k.ifBlank { "You May Also Like" }
    val cleaned = buildString {
        for (ch in rawTitle) {
            val cat = ch.category
            if (cat != CharCategory.OTHER_SYMBOL && cat != CharCategory.MODIFIER_SYMBOL) {
                append(ch)
            }
        }
    }.trim().ifBlank { "You May Also Like" }
    val title = when {
        cleaned.equals("More Like This", ignoreCase = true) -> "YOU MAY ALSO LIKE"
        cleaned.equals("You May Also Like", ignoreCase = true) -> "YOU MAY ALSO LIKE"
        cleaned.contains("recommend", ignoreCase = true) -> "RECOMMENDED FOR YOU"
        else -> cleaned.uppercase()
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Box(
                Modifier
                    .width(VideoPlayDimens.SectionBarWidth.dp)
                    .height(VideoPlayDimens.SectionBarHeight.dp)
                    .background(VideoPlayTokens.AccentGold, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                color = VideoPlayTokens.PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(VideoPlayDimens.GridGap.dp),
        ) {
            items(group.v, key = { it.localId() }) { item ->
                MediaPosterCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}
