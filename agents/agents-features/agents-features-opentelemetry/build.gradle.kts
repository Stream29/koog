import ai.koog.gradle.publish.maven.Publishing.publishToMaven

group = rootProject.group
version = rootProject.version

plugins {
    id("ai.kotlin.multiplatform")
    alias(libs.plugins.kotlin.serialization)
}

val rootProjectVersion = rootProject.version.toString()
val rootProjectGroup = rootProject.group.toString()

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

            resources.srcDir(layout.buildDirectory.dir("generated/resources"))
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

    // Configure JVM application executable
    jvm {
        @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
        mainRun {
            mainClass.set("ai.koog.agents.features.opentelemetry.server.OpenTelemetryServerAppKt")
        }
    }

    explicitApi()
}

val generateProductProperties = tasks.register("generateProductProperties") {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val propertiesFile = outputDir.get().file("product.properties")

    inputs.property("version", rootProjectVersion)
    inputs.property("group", rootProjectGroup)
    outputs.file(propertiesFile)

    doLast {
        propertiesFile.asFile.parentFile.mkdirs()
        propertiesFile.asFile.writeText("""
            version=$rootProjectVersion
            serviceName=$rootProjectGroup
        """.trimIndent())
    }
}


tasks.named("jvmProcessResources") {
    dependsOn(generateProductProperties)
}


publishToMaven()
