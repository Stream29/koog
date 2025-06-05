# OpenTelemetry for Koog AI Agents

This module provides OpenTelemetry integration for Koog AI Agents, allowing you to collect and analyze telemetry data from agent executions.

## Local OpenTelemetry Server

This module includes a local OpenTelemetry server that can be used for development and testing purposes.

### Prerequisites

- Docker installed and running
- Java 17+ installed

### Starting the Server

To start the OpenTelemetry server, run:

```bash
./gradlew :agents:agents-features:agents-features-opentelemetry:run
```

This will start:
1. Jaeger (UI available at http://localhost:16686)
2. OpenTelemetry Collector

The server will keep running until you press ENTER in the console.

### Integrating with Agents

To collect telemetry data from your agents, add the OpenTelemetry feature to your agent pipeline:

```kotlin
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry

val agent = AIAgent.builder()
    .withFeature(OpenTelemetry)
    .build()
```

### Viewing Telemetry Data

Once your agent executes with the OpenTelemetry feature enabled, you can view the collected data in the Jaeger UI at http://localhost:16686.

### Configuration

The OpenTelemetry collector configuration is defined in `otel-collector-config.yaml`. You can modify this file to customize the collector's behavior.

## Adding Custom Spans

You can add custom spans to your agent execution by accessing the OpenTelemetry tracer:

```kotlin
val tracer = OpenTelemetry.createInitialConfig().tracer
val span = tracer.spanBuilder("my-custom-span").startSpan()

try {
    // Your code here
} finally {
    span.end()
}
```
