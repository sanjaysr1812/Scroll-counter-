package com.scrollmeter.app.domain.usecases

import com.scrollmeter.app.data.repository.ScrollMeterRepository

/**
 * Exports all local daily swipe and session statistics as a clean RFC-4180 CSV string
 * for user-initiated local file saving.
 */
class ExportStatisticsCsvUseCase(
    private val repository: ScrollMeterRepository
) {
    suspend operator fun invoke(): String {
        return repository.exportDailyStatisticsCsv()
    }
}
