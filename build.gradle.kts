plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.fraaaa.stk"
version = "1.0.0"

application {
    mainClass = "MainKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.contentNegotiation)
    implementation(libs.exposed.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.exposed.jdbc)
    implementation(libs.logback.classic)
}
