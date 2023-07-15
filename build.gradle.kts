import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = "2.3.2"
val logbackVersion = "1.4.7"
val micrometerVersion = "1.11.1"
val prometheusVersion = "0.16.0"

plugins {
    kotlin("jvm") version "1.8.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.22"
}

group = "no.nav"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("io.prometheus:simpleclient:$prometheusVersion")
    implementation("io.prometheus:simpleclient_pushgateway:$prometheusVersion")


    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    named<KotlinCompile>("compileTestKotlin") {
        kotlinOptions.jvmTarget = "17"
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }


    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        manifest {
            attributes(
                mapOf(
                    "Main-Class" to "no.nav.MainKt"
                )
            )
        }
    }

    withType<Wrapper> {
        gradleVersion = "8.2.1"
    }
}