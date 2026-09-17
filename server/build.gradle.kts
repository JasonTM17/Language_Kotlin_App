plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    application
}

group = "com.linguaai"
version = "1.0.0"

application {
    mainClass.set("com.linguaai.server.ApplicationKt")
}

// detekt embeds its own Kotlin compiler, and 1.23.8's knows JVM targets only up
// to 22. The build runs on JDK 24, so detekt would default --jvm-target to 24 and
// refuse to start with "Invalid value (24) passed to --jvm-target". Point it at
// the target the project actually compiles to, which is what it should be
// analysing against anyway.
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
}

// Rules that genuinely do not fit this codebase, each with its reason, live in
// detekt.yml. Defaults still apply for everything the file does not mention.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
}

// detekt runs as part of `check`, and therefore as part of `build`. It was
// deliberately detached while the baseline was worked down from 126 findings to
// zero, on the principle that a gate should not be switched on before it can
// pass. Now that detekt passes, it is back in the build: `./gradlew build` fails
// on a lint regression.
//
// Do not detach this again to land a change faster. Fix the finding.

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Outbound AI provider client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Persistence
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.mysql.connector)
    implementation(libs.flyway.core)
    implementation(libs.flyway.mysql)

    // Security
    implementation(libs.bcrypt)

    // Logging
    implementation(libs.logback.classic)

    // Ships the H2 driver in the installDist output so the documented
    // no-Docker demo backend (jdbc:h2:file) runs without test dependencies.
    runtimeOnly(libs.h2)

    // Tests
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.h2)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
