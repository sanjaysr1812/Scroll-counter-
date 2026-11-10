package com.scrollmeter.app.domain.usecases

import com.scrollmeter.app.data.database.entities.DailyStatisticsEntity
import com.scrollmeter.app.data.database.entities.UserSettingsEntity

data class LimitStatusEvaluation(
    val combinedProgressRatio: Float,
    val youtubeProgressRatio: Float,
    val instagramProgressRatio: Float,
    val timeProgressRatio: Float,
    val remainingCombinedSwipes: Int,
    val activeWarningLevelPercent: Int?,
    val warningBannerMessage: String?
)

/**
 * Evaluates current daily swipe and duration totals against the user's configured limits
 * and warning thresholds (50%, 75%, 90%, 100%).
 */
class EvaluateLimitThresholdsUseCase {

    operator fun invoke(
        todayStats: DailyStatisticsEntity,
        settings: UserSettingsEntity
    ): LimitStatusEvaluation {
        val combinedLimit = settings.dailyCombinedLimit.coerceAtLeast(1)
        val ytLimit = settings.youtubeDailyLimit.coerceAtLeast(1)
        val igLimit = settings.instagramDailyLimit.coerceAtLeast(1)
        val timeLimitSec = (settings.timeLimitMinutes.coerceAtLeast(1)) * 60L

        val totalDurationSec = todayStats.youtubeDuration + todayStats.instagramDuration

        val combinedRatio = todayStats.totalSwipeCount.toFloat() / combinedLimit
        val ytRatio = todayStats.youtubeSwipeCount.toFloat() / ytLimit
        val igRatio = todayStats.instagramSwipeCount.toFloat() / igLimit
        val timeRatio = totalDurationSec.toFloat() / timeLimitSec

        val maxPct = (maxOf(combinedRatio, ytRatio, igRatio, timeRatio) * 100f).toInt()
        val remaining = (settings.dailyCombinedLimit - todayStats.totalSwipeCount).coerceAtLeast(0)

        val enabledThresholds = buildList {
            if (settings.warnAt50) add(50)
            if (settings.warnAt75) add(75)
            if (settings.warnAt90) add(90)
            if (settings.warnAt100) add(100)
        }.sorted()

        val reachedThreshold = enabledThresholds.lastOrNull { maxPct >= it }

        val bannerMessage = when {
            reachedThreshold == null -> null
            reachedThreshold >= 100 ->
                "You have reached 100% of your configured daily short-video allowance (${todayStats.totalSwipeCount} / ${settings.dailyCombinedLimit} swipes)."
            else ->
                "Heads up: You have reached $reachedThreshold% of your daily target ($remaining swipes remaining in your combined allowance)."
        }

        return LimitStatusEvaluation(
            combinedProgressRatio = combinedRatio,
            youtubeProgressRatio = ytRatio,
            instagramProgressRatio = igRatio,
            timeProgressRatio = timeRatio,
            remainingCombinedSwipes = remaining,
            activeWarningLevelPercent = reachedThreshold,
            warningBannerMessage = bannerMessage
        )
    }
}
