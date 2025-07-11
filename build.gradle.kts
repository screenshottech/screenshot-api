val kotlin_version: String by project
val logback_version: String by project
val koin_version: String by project
val prometheus_version: String by project
val jetbrains_exposed_version: String by project
val postgresql_version : String by project
val hikari_version : String by project
val microsoft_playwright_version : String by project
val aws_sdk_kotlin_version: String by project
val lettuce_version: String by project
val skiko_version: String by project
val cohort_ktor_version: String by project
val ktor_rate_limiting_version: String by project
val stripe_version: String by project
val mockk_version: String by project
val junit_version: String by project
val bcrypt_version: String by project
val jakarta_mail_version: String by project
val jakarta_mail_api_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

group = "dev.screenshotapi"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-openapi")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("io.ktor:ktor-server-rate-limit")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    implementation("io.github.flaxoos:ktor-server-rate-limiting:$ktor_rate_limiting_version")
    implementation("com.sksamuel.cohort:cohort-ktor:$cohort_ktor_version")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$kotlin_version")
    testImplementation("org.junit.jupiter:junit-jupiter:$junit_version")
    testImplementation("io.mockk:mockk:${mockk_version}")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Database
    implementation("org.jetbrains.exposed:exposed-core:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$jetbrains_exposed_version")
    implementation("org.postgresql:postgresql:$postgresql_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")

    // Redis
    implementation("io.lettuce:lettuce-core:$lettuce_version")

    // Screenshot
    implementation("com.microsoft.playwright:playwright:$microsoft_playwright_version")

    // Image processing (Skia for multiplatform WebP support)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skiko_version") // Mac ARM64 (local development)
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skiko_version") // Linux x64 (Docker/production)
    implementation("org.jetbrains.skiko:skiko:$skiko_version") // Core multiplatform

    // AWS S3
    implementation("aws.sdk.kotlin:s3:$aws_sdk_kotlin_version")

    // OpenAPI & Documentation
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-openapi")

    // HTTP Client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-serialization")

    // Security (BCrypt for hashing)
    implementation("org.mindrot:jbcrypt:$bcrypt_version")

    // Stripe Payment Gateway
    implementation("com.stripe:stripe-java:$stripe_version")

    // Monitoring
    implementation("io.ktor:ktor-server-metrics")
    implementation("io.ktor:ktor-server-metrics-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheus_version")


    // Email - Jakarta Mail for Gmail SMTP support
    implementation("com.sun.mail:jakarta.mail:$jakarta_mail_version")
    implementation("jakarta.mail:jakarta.mail-api:$jakarta_mail_api_version")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = false
    }

    doFirst {
        println("\n🧪 Running tests...")
    }

    doLast {
        println("\n✅ All tests passed successfully!")
    }

    maxHeapSize = "512m"
}

tasks.register("installGitHooks") {
    description = "Install git hooks for pre-commit testing"
    group = "git hooks"

    doLast {
        val gitHooksDir = file(".git/hooks")
        if (!gitHooksDir.exists()) {
            gitHooksDir.mkdirs()
        }

        val preCommitHook = file(".git/hooks/pre-commit")
        preCommitHook.writeText("""
            #!/bin/sh
            echo "🧪 Running tests before commit..."
            ./gradlew test --no-daemon --quiet
            if [ $? -ne 0 ]; then
                echo "❌ Tests failed! Commit aborted."
                echo "💡 Check: ./gradlew test --no-daemon"
                exit 1
            fi
            echo "✅ All tests passed! Proceeding with commit."
        """.trimIndent())
        preCommitHook.setExecutable(true)

        val prePushHook = file(".git/hooks/pre-push")
        prePushHook.writeText("""
            #!/bin/sh
            echo "🧪 Running tests before push..."
            ./gradlew test --no-daemon --quiet
            if [ $? -ne 0 ]; then
                echo "❌ Tests failed! Push aborted."
                echo "💡 Check: ./gradlew test --no-daemon"
                exit 1
            fi
            echo "✅ All tests passed! Proceeding with push."
        """.trimIndent())
        prePushHook.setExecutable(true)

        println("✅ Git hooks installed successfully!")
        println("   - pre-commit: runs tests before every commit")
        println("   - pre-push: runs tests before every push")
    }
}

tasks.named("build") {
    dependsOn("installGitHooks")
}
