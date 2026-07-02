plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.fraaaa.stk"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

tasks.jar.configure {
    manifest {
        attributes(mapOf(
            "Main-Class" to "MainKt",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        ))
    }
    
    // Fat JAR: include all dependencies
    from(configurations.runtimeClasspath.get().map { 
        if (it.isDirectory) it else zipTree(it) 
    })
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    implementation("io.ktor:ktor-client-cio:3.4.3")
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
