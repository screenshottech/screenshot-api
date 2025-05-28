package dev.screenshotapi.infrastructure.config

enum class Environment {
    LOCAL, DEVELOPMENT, STAGING, PRODUCTION, DOCKER;

    companion object {
        fun current(): Environment {
            val env = System.getenv("ENVIRONMENT") ?: "LOCAL"
            return valueOf(env.uppercase())
        }
    }

    val isLocal: Boolean get() = this == LOCAL || this == DOCKER
    val isProduction: Boolean get() = this == PRODUCTION
}
