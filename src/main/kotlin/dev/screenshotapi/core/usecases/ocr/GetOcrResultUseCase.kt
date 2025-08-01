package dev.screenshotapi.core.usecases.ocr

import dev.screenshotapi.core.domain.entities.OcrResult
import dev.screenshotapi.core.domain.repositories.OcrResultRepository
import dev.screenshotapi.core.domain.exceptions.ResourceNotFoundException
import dev.screenshotapi.core.domain.exceptions.AuthorizationException

/**
 * Get OCR Result Use Case - Retrieves OCR result by ID with user access control
 */
class GetOcrResultUseCase(
    private val ocrResultRepository: OcrResultRepository
) {
    data class Request(
        val resultId: String,
        val userId: String
    )

    suspend operator fun invoke(request: Request): OcrResult {
        val result = ocrResultRepository.findById(request.resultId)
            ?: throw ResourceNotFoundException("OCR result", request.resultId)

        // Verify ownership
        if (result.userId != request.userId) {
            throw AuthorizationException.JobNotAuthorized("Access denied to OCR result")
        }

        return result
    }
}