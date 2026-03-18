plugins {
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    application
}

group = "com.novel.writing.assistant"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor dependencies
    implementation("io.ktor:ktor-server-core:2.2.4")
    implementation("io.ktor:ktor-server-netty:2.2.4")
    implementation("io.ktor:ktor-server-content-negotiation:2.2.4")
    implementation("io.ktor:ktor-server-status-pages:2.2.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.2.4")
    implementation("io.ktor:ktor-client-core:2.2.4")
    implementation("io.ktor:ktor-client-okhttp:2.2.4")
    implementation("io.ktor:ktor-client-content-negotiation:2.2.4")
    
    // Logging dependencies
    implementation("ch.qos.logback:logback-classic:1.4.11")

    // Database dependencies
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.h2database:h2:2.2.224")
    
    // Testing dependencies
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.ktor:ktor-server-tests:2.2.4")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("com.novel.writing.assistant.ApplicationKt")
}
