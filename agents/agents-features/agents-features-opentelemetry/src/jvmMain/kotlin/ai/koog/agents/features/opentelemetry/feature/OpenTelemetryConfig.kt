package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.common.config.FeatureConfig
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter

/**
 * Configuration for the OpenTelemetry feature.
 *
 * This class allows you to configure how the OpenTelemetry SDK is set up, including:
 * - Service name and version
 * - Span exporters
 * - Sampling configuration
 * - Resource attributes
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(OpenTelemetry.Feature) {
 *         serviceName = "my-agent-service"
 *         serviceVersion = "1.0.0"
 *         addSpanExporter(LoggingSpanExporter.create())
 *         sampler = Sampler.alwaysOn()
 *         addResourceAttribute(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production")
 *     }
 * }
 * ```
 */
public class OpenTelemetryConfig : FeatureConfig() {
    /**
     * The name of the service being instrumented.
     * Default is "koog-agent".
     */
    public var serviceName: String = "koog-agent"

    /**
     * The version of the service being instrumented.
     * Default is "1.0.0".
     */
    public var serviceVersion: String = "1.0.0"

    /**
     * The list of span exporters to use.
     * By default, includes a LoggingSpanExporter.
     */
    private val spanExporters: MutableList<SpanExporter> = mutableListOf(LoggingSpanExporter.create())

    /**
     * The sampler to use for trace sampling.
     * Default is Sampler.alwaysOn().
     */
    public var sampler: Sampler = Sampler.alwaysOn()

    /**
     * Additional resource attributes to include with all telemetry.
     */
    private val resourceAttributes: MutableMap<String, String> = mutableMapOf()

    /**
     * Adds a span exporter to the configuration.
     */
    public fun addSpanExporter(exporter: SpanExporter) {
        spanExporters.add(exporter)
    }

    /**
     * Adds an OTLP gRPC span exporter with the specified endpoint.
     */
    public fun addOtlpExporter(endpoint: String = "http://localhost:4317") {
        spanExporters.add(
            OtlpGrpcSpanExporter.builder()
                .setEndpoint(endpoint)
                .build()
        )
    }

    /**
     * Adds a resource attribute to the configuration.
     */
    public fun addResourceAttribute(key: String, value: String) {
        resourceAttributes[key] = value
    }

    /**
     * Standard resource attribute keys
     */
    public companion object {
        public const val SERVICE_NAME: String = "service.name"
        public const val SERVICE_VERSION: String = "service.version"
        public const val DEPLOYMENT_ENVIRONMENT: String = "deployment.environment"
    }

    /**
     * Builds and returns a configured OpenTelemetrySDK instance.
     */
    internal fun buildSdk(): OpenTelemetrySdk {
        val resourceBuilder = Resource.getDefault().toBuilder()
            .put(SERVICE_NAME, serviceName)
            .put(SERVICE_VERSION, serviceVersion)

        val attributesBuilder = Attributes.builder()
        resourceAttributes.forEach { (key, value) ->
            attributesBuilder.put(key, value)
        }
        val resource = resourceBuilder.putAll(attributesBuilder.build()).build()

        val spanProcessors = spanExporters.map { exporter ->
            BatchSpanProcessor.builder(exporter).build()
        }

        val tracerProviderBuilder = SdkTracerProvider.builder()
            .setResource(resource)
            .setSampler(sampler)

        spanProcessors.forEach { processor ->
            tracerProviderBuilder.addSpanProcessor(processor)
        }

        val tracerProvider = tracerProviderBuilder.build()

        // Build and return the SDK
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
    }
}
