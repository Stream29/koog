package ai.koog.agents.example.features.otel.server

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Simple utility to launch local OpenTelemetry services for development
 */
public object OpenTelemetryServer {
    private const val OTEL_COLLECTOR_CONFIG = "otel-collector-config.yaml"
    private const val JAEGER_IMAGE = "jaegertracing/all-in-one:latest"
    private const val OTEL_COLLECTOR_IMAGE = "otel/opentelemetry-collector-contrib:latest"

    /**
     * Launches a local OpenTelemetry collector and Jaeger for tracing
     *
     * @param configPath Path to the OpenTelemetry collector config file
     * @return True if services started successfully
     */
    public fun launch(configPath: String = OTEL_COLLECTOR_CONFIG): Boolean {
        try {
            // Check if config file exists
            val configFile = File(
                "./examples/src/main/kotlin/ai/koog/agents/example/features",
                configPath
            )
            if (!configFile.exists()) {
                println("❌ OpenTelemetry collector config file not found at: $configPath")
                return false
            }

            // Stop any existing containers
            stopContainers()

            // Start Jaeger
            val jaegerCmd = "docker run -d --name jaeger -p 16686:16686 -p 14250:14250 -p 4317:4317 $JAEGER_IMAGE"
            println("Starting Jaeger: $jaegerCmd")
            val jaegerProcess = Runtime.getRuntime().exec(jaegerCmd)
            if (!jaegerProcess.waitFor(10, TimeUnit.SECONDS)) {
                println("❌ Jaeger startup timed out")
                return false
            }

            if (jaegerProcess.exitValue() != 0) {
                println("❌ Failed to start Jaeger: ${jaegerProcess.errorStream.bufferedReader().readText()}")
                return false
            }

            println("✅ Jaeger started successfully")
            println("   Jaeger UI available at: http://localhost:16686")

            // Start OTEL Collector
            val otelCmd = "docker run -d --name otel-collector -p 4318:4318 -v ${configFile.absolutePath}:/etc/otel-collector-config.yaml $OTEL_COLLECTOR_IMAGE --config=/etc/otel-collector-config.yaml"
            println("Starting OpenTelemetry Collector: $otelCmd")
            val otelProcess = Runtime.getRuntime().exec(otelCmd)
            if (!otelProcess.waitFor(10, TimeUnit.SECONDS)) {
                println("❌ OpenTelemetry Collector startup timed out")
                return false
            }

            if (otelProcess.exitValue() != 0) {
                println("❌ Failed to start OpenTelemetry Collector: ${otelProcess.errorStream.bufferedReader().readText()}")
                return false
            }

            println("✅ OpenTelemetry Collector started successfully")
            return true
        } catch (e: Exception) {
            println("❌ Error launching OpenTelemetry services: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Stops all running OpenTelemetry services
     */
    public fun stop() {
        stopContainers()
    }

    private fun stopContainers() {
        try {
            println("Stopping existing containers...")
            Runtime.getRuntime().exec("docker stop otel-collector jaeger").waitFor(5, TimeUnit.SECONDS)
            Runtime.getRuntime().exec("docker rm otel-collector jaeger").waitFor(5, TimeUnit.SECONDS)
            println("✅ Existing containers stopped and removed")
        } catch (e: Exception) {
            println("⚠️ Failed to stop containers: ${e.message}")
        }
    }
}

