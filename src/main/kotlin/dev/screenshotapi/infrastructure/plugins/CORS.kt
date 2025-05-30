import dev.screenshotapi.infrastructure.config.Environment
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*


fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Webhook-URL")


        val environment = Environment.current()
        if (environment.isLocal) {
            anyHost()
        } else {
            allowHost("yourdomain.com")
            allowHost("app.yourdomain.com")
        }

        allowCredentials = true
    }
}
