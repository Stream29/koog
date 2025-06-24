package ai.koog.agents.example.features.otel.server

import java.io.File

/**
 * Main function for launching a local OpenTelemetry development environment
 */
public fun main(args: Array<String>) {
    val configPath = args.firstOrNull() ?: "otel-collector-config.yaml"

    // Ensure the config file exists, if not, create it
    val configFile = File(configPath)
    if (!configFile.exists()) {
        println("Creating default OpenTelemetry collector config at: ${configFile.absolutePath}")
        configFile.writeText(DEFAULT_CONFIG)
    }

    val serverStarted = OpenTelemetryServer.launch(configPath)
    if (serverStarted) {
        println("\n🚀 OpenTelemetry environment is ready!")
        println("Jaeger UI: http://localhost:16686")
        println("\nPress ENTER to stop the servers...")
        readLine()
        OpenTelemetryServer.stop()
        println("\n👋 OpenTelemetry environment stopped")
    } else {
        println("\n❌ Failed to start OpenTelemetry environment")
    }
}

private const val DEFAULT_CONFIG = """receivers:
  otlp:
    protocols:
      grpc:
      http:

processors:
  batch:

exporters:
  logging:
    verbosity: detailed
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

extensions:
  health_check:

service:
  extensions: [health_check]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [logging, jaeger]
    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [logging]
    logs:
      receivers: [otlp]
      processors: [batch]
      exporters: [logging]
"""
