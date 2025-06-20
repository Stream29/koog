import ai.grazie.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

//repositories {
//    maven(url = "https://maven.pkg.github.com/dcxp/opentelemetry-kotlin")
//}
//
//dependencies {
//    implementation("io.opentelemetry.kotlin.api:all:VERSION")
//    implementation("io.opentelemetry.kotlin.api:metrics:VERSION")
//    implementation("io.opentelemetry.kotlin.sdk:sdk-metrics:VERSION")
//    implementation("io.opentelemetry.kotlin.sdk:sdk-trace:VERSION")
//}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-features:agents-features-common"))
                api(project(":agents:agents-features:agents-features-trace"))

                api(libs.kotlinx.serialization.json)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.opentelemetry.api)
                implementation(libs.opentelemetry.extension.kotlin)
                implementation(libs.opentelemetry.sdk)
                implementation(libs.opentelemetry.exporter.otlp)
                implementation(libs.opentelemetry.exporter.jaeger)
                implementation(libs.opentelemetry.exporter.zipkin)
                implementation(libs.opentelemetry.exporter.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }
    }

    explicitApi()
}

publishToMaven()
