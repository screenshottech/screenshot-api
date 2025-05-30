val kotlin_version: String by project
val logback_version: String by project
val koin_version: String by project
val prometheus_version: String by project
val jetbrains_exposed_version: String by project
val postgresql_version : String by project
val hikari_version : String by project
val microsoft_playwright_version : String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.1.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
}

group = "dev.screenshotapi"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-swagger")
    implementation("io.ktor:ktor-server-openapi")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    implementation("io.github.flaxoos:ktor-server-rate-limiting:2.2.1")
    implementation("com.sksamuel.cohort:cohort-ktor:2.7.2")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$jetbrains_exposed_version")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$jetbrains_exposed_version")
    implementation("org.postgresql:postgresql:$postgresql_version")
    implementation("com.zaxxer:HikariCP:$hikari_version")

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")

    // Screenshot
    implementation("com.microsoft.playwright:playwright:$microsoft_playwright_version")

    // AWS S3
    implementation("aws.sdk.kotlin:s3:1.0.30")

    // HTTP Client
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-client-serialization")

    // Monitoring
    implementation("io.ktor:ktor-server-metrics")
    implementation("io.ktor:ktor-server-metrics-micrometer")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheus_version")
}
