package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.repositories.OcrResultRepository

/**
 * List OCR Results Use Case - Lists user's OCR results with pagination
 */
class ListOcrResultsUseCase(
    private val ocrResultRepository: OcrResultRepository
) {
    data class Request(
        val userId: String,
        val offset: Int,
        val limit: Int
    )

    suspend operator fun invoke(request: Request): List<OcrResult> {
        return ocrResultRepository.findByUserId(
            userId = request.userId,
            offset = request.offset,
            limit = request.limit
        )
    }
}