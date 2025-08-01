package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.repositories.OcrAnalytics
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import kotlinx.datetime.Instant

/**
 * Get OCR Analytics Use Case - Retrieves user's OCR analytics
 */
class GetOcrAnalyticsUseCase(
    private val ocrResultRepository: OcrResultRepository
) {
    data class Request(
        val userId: String,
        val fromDate: Instant,
        val toDate: Instant
    )

    suspend operator fun invoke(request: Request): OcrAnalytics {
        return ocrResultRepository.getUserOcrAnalytics(
            userId = request.userId,
            fromDate = request.fromDate,
            toDate = request.toDate
        )
    }
}