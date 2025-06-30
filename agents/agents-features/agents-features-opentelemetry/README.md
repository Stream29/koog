# OpenTelemetry support

This guide provides comprehensive instructions on how to use OpenTelemetry with the Koog agentic framework for tracing and monitoring your AI agents.

## Overview

OpenTelemetry is an observability framework that provides tools for generating, collecting, and exporting telemetry data (metrics, logs, and traces) from your applications. The Koog OpenTelemetry feature allows you to instrument your AI agents to collect telemetry data, which can help you:

- Monitor agent performance and behavior
- Debug issues in complex agent workflows
- Visualize the execution flow of your agents
- Track LLM calls and tool usage
- Analyze agent behavior patterns

Key OpenTelemetry concepts

- **Spans**: Spans represent individual units of work or operations within a distributed trace. They indicate the beginning and end of a specific activity in an application, such as an agent execution, a function call, an LLM call, or a tool call.
- **Exporters**: Exporters are components responsible for sending the collected telemetry data (spans, metrics, logs) to various backends or destinations.
- **Collectors**: An OpenTelemetry Collector receives, processes, and exports telemetry data. It acts as an intermediary between your applications and your observability backend.

The OpenTelemetry feature in Koog automatically creates spans for various agent events, including:

- Agent execution start and end
- Node execution
- LLM calls
- Tool calls

## Installation

To use OpenTelemetry with Koog, add the OpenTelemetry feature to your agent:

```kotlin
val agent = AIAgent(
    executor = simpleOpenAIExecutor(apiKey),
    llmModel = OpenAIModels.Chat.GPT4o,
    systemPrompt = "You are a helpful assistant.",
    installFeatures = {
        install(OpenTelemetry) {
            // Configuration options here
        }
    }
)
```

## Configuration

### Basic configuration

Here is an example of installing the OpenTelemetry feature with a basic set of configuration items:

```kotlin
install(OpenTelemetry) {
    serviceName = "my-agent-service"  // Name of your service
    serviceVersion = "1.0.0"          // Version of your service
    addOtlpExporter()                 // Add OTLP exporter with default settings
}
```

The example includes the following configuration items:

| Name             | Data type | Required | Default      | Description                                    |
|------------------|-----------|----------|--------------|------------------------------------------------|
| `serviceName`    | String    | No       | `koog-agent` | The name of the service being instrumented.    |
| `serviceVersion` | String    | No       | `1.0.0`      | The version of the service being instrumented. |

In addition, the example also includes the `addOtlpExporter` function that adds the predefined OTLP gRPC span exporter
with default settings (endpoint set to `http://localhost:4317`). For more information, see [Exporters](#exporters).

### Advanced configuration

For more advanced configuration, you can also customize the following configuration options:

- Sampler: configure the sampling strategy to adjust the frequency and amount of collected data.
- Resource attributes: add resource information, either through standard or custom resource attributes.

```kotlin
install(OpenTelemetry) {
    serviceName = "my-agent-service"
    serviceVersion = "1.0.0"
    sampler = Sampler.alwaysOn()  // Configure the sampling strategy
    
    // Add resource attributes
    addResourceAttribute(OpenTelemetryConfig.DEPLOYMENT_ENVIRONMENT, "production")
    addResourceAttribute("custom.attribute", "custom-value")
}
```

#### Sampler

To define a sampler, use a corresponding method of the `Sampler` class that represents the sampling strategy you want
to use. The available method is:

- `alwaysOn()`: The default sampling strategy where every span (trace) is sampled.

#### Resource attributes

Resource attributes represent additional information about a process producing telemetry. This information can be
included in an OpenTelemetry configuration in Koog using the `addResourceAttribute()` method that takes a key and
a string value as its arguments. Standard resource attribute keys are provided as constants such as `OpenTelemetryConfig.DEPLOYMENT_ENVIRONMENT` and are as follows:

- `SERVICE_NAME`: The name of the service being instrumented. Automatically inferred from the `serviceName` property, if set.
- `SERVICE_VERSION`: The version of the service being instrumented. Automatically inferred from the `serviceVersion` property, if set.
- `DEPLOYMENT_ENVIRONMENT`: The label for the deployment environment where the process is running. For example, `production`.

The OpenTelemetry feature automatically adds various attributes to spans:

- `koog.event.strategy.name`: the name of the agent strategy
- `koog.event.eventId`: the ID of the event
- `koog.event.result`: the result of the agent execution
- `koog.llm.call.prompt`: the prompt sent to the LLM
- `koog.llm.call.responses`: the responses received from the LLM
- `koog.tool.name`: the name of the tool being called
- `koog.tool.args`: the arguments passed to the tool
- `koog.tool.call.result`: indicates a successful tool call result

## Exporters

Exporters send collected telemetry data to an OpenTelemetry Collector or other types of destinations or backend implementations.

### OTLP Exporter

The OTLP (OpenTelemetry Protocol) exporter sends telemetry data to an OpenTelemetry Collector. This is useful for integrating with systems like Jaeger, Zipkin, or Prometheus.

To add an OpenTelemetry Exporter, use the `addOtlpExporter` function. The function takes a single argument:

| Name       | Data type | Required | Default                 | Description                                                                                                                               |
|------------|-----------|----------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `endpoint` | String    | No       | `http://localhost:4317` | The address of the OpenTelemetry backend or Collector for the collected telemetry data. |

```kotlin
install(OpenTelemetry) {
    // The default endpoint is http://localhost:4317
    addOtlpExporter()
    
    // Specify a custom endpoint
    addOtlpExporter("http://my-otel-collector:4317")
}
```

### Logging Exporter

A logging exporter that outputs trace information to the console is included by default. This type of export is useful
for development and debugging purposes.

```kotlin
install(OpenTelemetry) {
    // The logging exporter is added by default
    addSpanExporter(LoggingSpanExporter.create())
   // You can add additional exporters as needed
}
```

## Integration with Jaeger

Jaeger is a popular distributed tracing system that works with OpenTelemetry. To use Jaeger with Koog:

1. Start a Jaeger container:
```yaml
# docker-compose.yaml
services:
  jaeger-all-in-one:
    image: jaegertracing/all-in-one:1.39
    container_name: jaeger-all-in-one
    environment:
      - COLLECTOR_OTLP_ENABLED=true
    ports:
      - "4317:4317"  # OTLP gRPC port
      - "16686:16686"  # Jaeger UI port
```

2. Configure your agent to use the OTLP exporter:
```kotlin
install(OpenTelemetry) {
    serviceName = "my-agent-service"
    addOtlpExporter()  // The default endpoint is http://localhost:4317
}
```

3. Access the Jaeger UI at `http://localhost:16686` to view your traces.

## Examples

This section includes two examples of implementing OpenTelemetry support in your agent workflow. The basic example
includes quick OpenTelemetry setup with minimal configuration, while the advanced example shows how you can create a
more elaborate and customized OpenTelemetry configuration.

### Basic example

```kotlin
suspend fun main() {
    val apiKey = "your-api-key"
    val agent = AIAgent(
        executor = simpleGoogleAIExecutor(apiKey),
        llmModel = GoogleModels.Gemini2_0Flash,
        systemPrompt = "You are a code assistant. Provide concise code examples.",
        installFeatures = {
            install(OpenTelemetry) {
                serviceName = "my-otlp-agent"
                addOtlpExporter()
            }
        }
    )

    val result = agent.run("Create python function that prints hello world")
    println(result)
    
    // Wait for telemetry data to be exported
    TimeUnit.SECONDS.sleep(10)
}
```

### Advanced example with custom exporters and attributes

```kotlin
suspend fun main() {
    val apiKey = "your-api-key"
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(apiKey),
        llmModel = OpenAIModels.Chat.GPT4o,
        systemPrompt = "You are a helpful assistant.",
        installFeatures = {
            install(OpenTelemetry) {
                serviceName = "advanced-agent"
                serviceVersion = "2.0.0"
                
                // Configure sampling
                sampler = Sampler.alwaysOn()
                
                // Add resource attributes
                addResourceAttribute(OpenTelemetryConfig.DEPLOYMENT_ENVIRONMENT, "staging")
                addResourceAttribute("code.source", "documentation-example")
                
                // Add exporters
                addOtlpExporter("http://otel-collector:4317")
                addSpanExporter(LoggingSpanExporter.create())
            }
        }
    )

    val result = agent.run("Tell me about OpenTelemetry")
    println(result)
    
    // Wait for telemetry data to be exported
    TimeUnit.SECONDS.sleep(10)
}
```

## Troubleshooting

### Common Issues

1. **No traces appearing in Jaeger UI**
    - Ensure the Jaeger container is running and the OTLP port (4317) is accessible
    - Check that the OTLP exporter is configured with the correct endpoint
    - Make sure to wait a few seconds after agent execution for traces to be exported

2. **Missing spans or incomplete traces**
    - Verify that the agent execution completes successfully
    - Ensure that you're not closing the application too quickly after agent execution
    - Add a delay (e.g., `TimeUnit.SECONDS.sleep(10)`) after agent execution to allow time for spans to be exported

3. **Excessive number of spans**
    - Consider using a different sampling strategy by configuring the `sampler` property
    - For example, use `Sampler.traceIdRatioBased(0.1)` to sample only 10% of traces