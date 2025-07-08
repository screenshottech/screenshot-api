package dev.screenshotapi.infrastructure.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.request.header
import io.ktor.server.response.respond

/**
 * Represents an API Key authentication provider.
 */
class ApiKeyAuthenticationProvider internal constructor(
    configuration: Configuration
) : AuthenticationProvider(configuration) {
    private val headerName: String = configuration.headerName

    private val authenticationFunction = configuration.authenticationFunction

    private val challengeFunction = configuration.challengeFunction

    private val authScheme = configuration.authScheme

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val apiKey = context.call.request.header(headerName)
        val principal = apiKey?.let { authenticationFunction(context.call, it) }

        val cause = when {
            apiKey == null -> AuthenticationFailedCause.NoCredentials
            principal == null -> AuthenticationFailedCause.InvalidCredentials
            else -> null
        }

        if (cause != null) {
            context.challenge(authScheme, cause) { challenge, call ->
                challengeFunction(call)

                challenge.complete()
            }
        }
        if (principal != null) {
            context.principal(principal)
        }
    }

    /**
     * Api key auth configuration.
     */
    class Configuration internal constructor(name: String?) : Config(name) {

        internal lateinit var authenticationFunction: suspend ApplicationCall.(String) -> Any?

        internal var challengeFunction: suspend (ApplicationCall) -> Unit = { call ->
            call.respond(HttpStatusCode.Unauthorized)
        }

        /**
         * Name of the scheme used when challenge fails, see [AuthenticationContext.challenge].
         */
        var authScheme: String = "apiKey"

        /**
         * Name of the header that will be used as a source for the api key.
         */
        var headerName: String = "X-API-Key"

        /**
         * Sets a validation function that will check given API key retrieved from [headerName] instance and return a principal,
         * or null if credential does not correspond to an authenticated principal.
         */
        fun validate(body: suspend ApplicationCall.(String) -> Any?) {
            authenticationFunction = body
        }

        /**
         * A response to send back if authentication failed.
         */
        fun challenge(body: suspend (ApplicationCall) -> Unit) {
            challengeFunction = body
        }
    }
}

/**
 * Installs API Key authentication mechanism.
 */
fun AuthenticationConfig.apiKey(
    name: String? = null,
    configure: ApiKeyAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = ApiKeyAuthenticationProvider(ApiKeyAuthenticationProvider.Configuration(name).apply(configure))
    register(provider)
}

