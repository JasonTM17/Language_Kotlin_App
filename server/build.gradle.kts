plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
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

// detekt attaches itself to `check`, which `build` depends on, so landing the
// plugin with an unclean baseline would break the existing CI immediately. The
// task is therefore manual while the baseline is worked down:
//
//     ./gradlew detekt
//
// Re-attach it to `check` — and make it a blocking CI step — only once the
// baseline is clean. Detaching is sequencing, not suppression: the gate is not
// being weakened, it is simply not switched on before it can pass.
tasks.matching { it.name == "check" }.configureEach {
    dependsOn.removeAll { it.toString().contains("detekt") }
}

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
