package com.scrollmeter.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.scrollmeter.app.data.database.dao.HourlyBucketCount
import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.domain.models.Platform
import com.scrollmeter.app.ui.theme.ElectricPurplePrimary
import com.scrollmeter.app.ui.theme.InstagramReelsMagenta
import com.scrollmeter.app.ui.theme.YouTubeShortsRed

/**
 * 1. Daily Swipe-Count Line Chart
 * Uses actual Room [DailyStatisticsEntity] records. Displays a clean empty state when no records exist.
 */
@Composable
fun DailySwipeLineChart(
    dailyStats: List<DailyStatisticsEntity>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty() || dailyStats.all { it.totalSwipeCount == 0 }) {
        ChartEmptyState(
            message = "No confirmed swipe history recorded for this period yet.",
            modifier = modifier
        )
        return
    }

    val maxSwipes = dailyStats.maxOf { it.totalSwipeCount }.coerceAtLeast(10)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            val width = size.width
            val height = size.height
            val count = dailyStats.size

            // Horizontal guide lines
            for (i in 0..3) {
                val y = height * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (count == 1) {
                val y = height - (dailyStats[0].totalSwipeCount.toFloat() / maxSwipes) * height
                drawCircle(
                    color = ElectricPurplePrimary,
                    radius = 5.dp.toPx(),
                    center = Offset(width / 2f, y)
                )
                return@Canvas
            }

            val stepX = width / (count - 1).coerceAtLeast(1)
            val path = Path()
            dailyStats.forEachIndexed { index, item ->
                val x = index * stepX
                val y = height - (item.totalSwipeCount.toFloat() / maxSwipes) * height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = ElectricPurplePrimary,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            dailyStats.forEachIndexed { index, item ->
                val x = index * stepX
                val y = height - (item.totalSwipeCount.toFloat() / maxSwipes) * height
                drawCircle(
                    color = ElectricPurplePrimary,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dailyStats.first().date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Peak: $maxSwipes swipes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = dailyStats.last().date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 2. YouTube Shorts vs Instagram Reels Comparison Bar Chart
 */
@Composable
fun PlatformComparisonBarChart(
    dailyStats: List<DailyStatisticsEntity>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty() || dailyStats.all { it.totalSwipeCount == 0 }) {
        ChartEmptyState(
            message = "No YouTube Shorts or Instagram Reels comparisons recorded yet.",
            modifier = modifier
        )
        return
    }

    val maxDayValue = dailyStats.maxOf { maxOf(it.youtubeSwipeCount, it.instagramSwipeCount) }.coerceAtLeast(10)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            val width = size.width
            val height = size.height
            val groupCount = dailyStats.size.coerceAtLeast(1)
            val groupWidth = width / groupCount
            val barWidth = (groupWidth * 0.34f).coerceAtMost(24.dp.toPx())

            drawLine(
                color = gridColor,
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 1.dp.toPx()
            )

            dailyStats.forEachIndexed { idx, stat ->
                val groupStart = idx * groupWidth + (groupWidth - barWidth * 2.2f) / 2f
                val ytHeight = (stat.youtubeSwipeCount.toFloat() / maxDayValue) * height
                val igHeight = (stat.instagramSwipeCount.toFloat() / maxDayValue) * height

                if (ytHeight > 0f) {
                    drawRoundRect(
                        color = YouTubeShortsRed,
                        topLeft = Offset(groupStart, height - ytHeight),
                        size = Size(barWidth, ytHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                if (igHeight > 0f) {
                    drawRoundRect(
                        color = InstagramReelsMagenta,
                        topLeft = Offset(groupStart + barWidth * 1.2f, height - igHeight),
                        size = Size(barWidth, igHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = YouTubeShortsRed, label = "YouTube Shorts")
            LegendItem(color = InstagramReelsMagenta, label = "Instagram Reels")
        }
    }
}

/**
 * 3. Hourly Activity Distribution Chart (00:00 - 23:00)
 */
@Composable
fun HourlyActivityChart(
    hourlyBuckets: List<HourlyBucketCount>,
    platformFilter: Platform? = null,
    modifier: Modifier = Modifier
) {
    val countsByHour = IntArray(24)
    for (bucket in hourlyBuckets) {
        if (bucket.hourOfDay in 0..23) {
            if (platformFilter == null || bucket.platform == platformFilter.id) {
                countsByHour[bucket.hourOfDay] += bucket.swipeCount
            }
        }
    }

    val maxHourCount = countsByHour.maxOrNull()?.coerceAtLeast(1) ?: 1
    if (countsByHour.all { it == 0 }) {
        ChartEmptyState(
            message = "No hourly swipe events recorded for this selection.",
            modifier = modifier
        )
        return
    }

    val barColor = when (platformFilter) {
        Platform.YOUTUBE_SHORTS -> YouTubeShortsRed
        Platform.INSTAGRAM_REELS -> InstagramReelsMagenta
        null -> ElectricPurplePrimary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(vertical = 8.dp)
        ) {
            val slotWidth = size.width / 24f
            val barWidth = (slotWidth * 0.65f).coerceAtLeast(3.dp.toPx())

            for (hour in 0..23) {
                val value = countsByHour[hour]
                val ratio = value.toFloat() / maxHourCount
                val barH = (ratio * size.height).coerceAtLeast(2.dp.toPx())
                val left = hour * slotWidth + (slotWidth - barWidth) / 2f

                drawRoundRect(
                    color = if (value > 0) barColor else barColor.copy(alpha = 0.14f),
                    topLeft = Offset(left, size.height - barH),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("06:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("18:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 4. Daily Time-Spent Chart (Estimated Short-Feed Minutes)
 */
@Composable
fun DailyTimeSpentChart(
    dailyStats: List<DailyStatisticsEntity>,
    modifier: Modifier = Modifier
) {
    if (dailyStats.isEmpty() || dailyStats.all { (it.youtubeDuration + it.instagramDuration) == 0L }) {
        ChartEmptyState(
            message = "No short-video feed duration recorded for this date range yet.",
            modifier = modifier
        )
        return
    }

    val maxMinutes = dailyStats.maxOf { ((it.youtubeDuration + it.instagramDuration) / 60f) }.coerceAtLeast(5f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            val count = dailyStats.size.coerceAtLeast(1)
            val slotW = size.width / count
            val barW = (slotW * 0.55f).coerceAtMost(28.dp.toPx())

            dailyStats.forEachIndexed { idx, stat ->
                val ytMin = stat.youtubeDuration / 60f
                val igMin = stat.instagramDuration / 60f
                val ytH = (ytMin / maxMinutes) * size.height
                val igH = (igMin / maxMinutes) * size.height
                val x = idx * slotW + (slotW - barW) / 2f

                if (ytH > 0f) {
                    drawRoundRect(
                        color = YouTubeShortsRed,
                        topLeft = Offset(x, size.height - ytH),
                        size = Size(barW, ytH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
                if (igH > 0f) {
                    drawRoundRect(
                        color = InstagramReelsMagenta,
                        topLeft = Offset(x, size.height - ytH - igH),
                        size = Size(barW, igH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
        Text(
            text = "Estimated feed session time (stacked YouTube Shorts + Instagram Reels)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ChartEmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "0 Confirmed Records",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
