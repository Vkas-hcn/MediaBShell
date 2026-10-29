package com.hdvideoplay.media.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom line-style chrome icons for My screen (24dp viewport, stroke ~1.5–2).
 * Bottom-nav tabs use res/drawable/ic_nav_*.xml via painterResource.
 */
object NavIcons {

    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // gear outer (simplified hexagon-ish circle + teeth via rounded rects)
                moveTo(12f, 15.5f)
                arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, -7f)
                arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, 7f)
                close()
                // top tooth
                moveTo(11f, 3.5f)
                horizontalLineTo(13f)
                verticalLineTo(6f)
                horizontalLineTo(11f)
                close()
                // bottom
                moveTo(11f, 18f)
                horizontalLineTo(13f)
                verticalLineTo(20.5f)
                horizontalLineTo(11f)
                close()
                // left
                moveTo(3.5f, 11f)
                horizontalLineTo(6f)
                verticalLineTo(13f)
                horizontalLineTo(3.5f)
                close()
                // right
                moveTo(18f, 11f)
                horizontalLineTo(20.5f)
                verticalLineTo(13f)
                horizontalLineTo(18f)
                close()
                // diag TL
                moveTo(6.2f, 5.5f)
                lineTo(7.9f, 7.2f)
                // diag TR
                moveTo(17.8f, 5.5f)
                lineTo(16.1f, 7.2f)
                // diag BL
                moveTo(6.2f, 18.5f)
                lineTo(7.9f, 16.8f)
                // diag BR
                moveTo(17.8f, 18.5f)
                lineTo(16.1f, 16.8f)
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_history",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 20.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, false, -8.2f, -6.5f)
                moveTo(4f, 11.5f)
                lineTo(4f, 15.5f)
                lineTo(8f, 15.5f)
                moveTo(12f, 8f)
                verticalLineTo(12.5f)
                lineTo(15f, 14.5f)
            }
        }.build()
    }

    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 4f)
                verticalLineTo(15f)
                moveTo(8f, 11.5f)
                lineTo(12f, 15.5f)
                lineTo(16f, 11.5f)
                moveTo(5f, 19.5f)
                horizontalLineTo(19f)
            }
        }.build()
    }

    val Favorite: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_favorite",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19.5f)
                curveTo(12f, 19.5f, 4.5f, 14.5f, 4.5f, 9.5f)
                curveTo(4.5f, 6.5f, 6.8f, 4.5f, 9.2f, 4.5f)
                curveTo(10.6f, 4.5f, 11.5f, 5.2f, 12f, 6f)
                curveTo(12.5f, 5.2f, 13.4f, 4.5f, 14.8f, 4.5f)
                curveTo(17.2f, 4.5f, 19.5f, 6.5f, 19.5f, 9.5f)
                curveTo(19.5f, 14.5f, 12f, 19.5f, 12f, 19.5f)
                close()
            }
        }.build()
    }

    val About: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_about",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 20.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
                close()
                moveTo(12f, 11f)
                verticalLineTo(16.5f)
                moveTo(12f, 8f)
                verticalLineTo(8.01f)
            }
        }.build()
    }

    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ic_chevron_right",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }.build()
    }
}
