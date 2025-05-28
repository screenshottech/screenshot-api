package dev.screenshotapi.core.usecases.common

interface UseCase<in Request, out Response> {
    suspend operator fun invoke(request: Request): Response
}

