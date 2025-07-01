package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.common.config.FeatureConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Configuration class for OpenTelemetry integration.
 *
 * Provides seamless integration with the OpenTelemetry SDK, allowing initialization
 * and customization of various components such as the tracer, meter, exporters, etc.
 */
public class OpenTelemetryConfig : FeatureConfig() {

    private companion object {

        private val logger = KotlinLogging.logger { }

        private val osName = System.getProperty("os.name")

        private val osVersion = System.getProperty("os.version")

        private val osArch = System.getProperty("os.arch")
    }

    private val productProperties = run {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("product.properties")?.use { stream ->
            props.load(stream)
        }
        props
    }

    private val customSpanExporters = mutableListOf<SpanExporter>()

    private val customSpanProcessorsCreator = mutableListOf<(SpanExporter) -> SpanProcessor>()

    /**
     * Adds a SpanExporter to the OpenTelemetry configuration. This exporter will
     * be used to export spans collected during the application's execution.
     *
     * @param exporter The SpanExporter instance to be added to the list of custom span exporters.
     */
    public fun addSpanExporter(exporter: SpanExporter) {
        customSpanExporters.add(exporter)
    }

    /**
     * Adds a SpanProcessor creator function to the OpenTelemetry configuration.
     *
     * @param createProcessor A function that takes a SpanExporter and returns a SpanProcessor. This allows
     * defining custom logic for processing spans before they are exported.
     */
    public fun addSpanProcessor(createProcessor: (SpanExporter) -> SpanProcessor) {
        customSpanProcessorsCreator.add(createProcessor)
    }

    /**
     * Specifies the name of the service used for OpenTelemetry configuration.
     */
    public var serviceName: String = productProperties.getProperty("product.name") ?: "ai.koog"

    /**
     * Represents the version identifier for the service in the OpenTelemetry configuration.
     */
    public var serviceVersion: String = productProperties.getProperty("product.name") ?: ""

    /**
     * The namespace to which the service belongs in the OpenTelemetry configuration.
     * This optional property can be used to logically group services under a common namespace.
     */
    public var serviceNamespace: String? = null

    /**
     * Controls whether verbose telemetry data should be captured during application execution.
     * When set to `true`, the application collects more detailed telemetry data.
     * This option can be useful for debugging and fine-grained monitoring but may impact performance.
     *
     * Default value is `false`, meaning verbose data capture is disabled.
     */
    public var captureVerboseData: Boolean = false


    private var _instrumentationScopeName: String = serviceName

    private var _sdk: OpenTelemetrySdk? = null

    /**
     * Provides an instance of the `OpenTelemetrySdk`.
     *
     * This property retrieves the existing instance of the SDK if it has already been initialized. If the SDK has not
     * been initialized, it initializes a new instance with the specified service name, service version, and optional
     * service namespace. The initialized SDK instance is cached for future access.
     *
     * The `initializeOpenTelemetry` function configures the SDK with the appropriate service attributes, trace
     * providers, span processors, and exporters. It also ensures proper shutdown of the SDK on application termination.
     *
     * @return The initialized or previously cached `OpenTelemetrySdk`.
     */
    public val sdk: OpenTelemetrySdk
        get() = _sdk
            ?: initializeOpenTelemetry(serviceName, serviceVersion, serviceNamespace)
                .also {
                    _sdk = it
                    _instrumentationScopeName = serviceName
                }

    /**
     * Provides a configured instance of [Meter] for obtaining OpenTelemetry metrics.
     */
    public val meter: Meter
        get() = sdk.getMeter(_instrumentationScopeName)

    /**
     * Provides access to the `Tracer` instance for tracking and recording tracing data.
     */
    public val tracer: Tracer
        get() = sdk.getTracer(_instrumentationScopeName)

    //region Private Methods

    private fun initializeOpenTelemetry(
        serviceName: String,
        serviceVersion: String,
        serviceNamespace: String? = null
    ): OpenTelemetrySdk {

        // SDK
        val builder = OpenTelemetrySdk.builder()

        // Tracing
        val resource = createResources(serviceName, serviceVersion, serviceNamespace, osName, osVersion, osArch)
        val exporters: List<SpanExporter> = createExporters()

        val traceProviderBuilder = SdkTracerProvider.builder()
            .setSampler(Sampler.alwaysOn())
            .setResource(resource)

        exporters.forEach { exporter: SpanExporter ->
            traceProviderBuilder.addProcessors(exporter)
        }

        val sdk = builder
            .setTracerProvider(traceProviderBuilder.build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

        // Add a hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(Thread { sdk.close() })

        return sdk
    }

    @Suppress("SameParameterValue")
    private fun createResources(
        serviceName: String,
        serviceVersion: String,
        serviceNamespace: String? = null,
        osName: String? = null,
        osVersion: String? = null,
        osArch: String? = null,
    ): Resource {
        val resourceAttributesBuilder = Attributes.builder()
        resourceAttributesBuilder
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .put(AttributeKey.stringKey("service.version"), serviceVersion)
            .put(AttributeKey.stringKey("service.namespace"), serviceNamespace)
            .put(AttributeKey.stringKey("service.instance.time"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))

        osName?.let { osName -> resourceAttributesBuilder.put(AttributeKey.stringKey("os.type"), osName) }
        osVersion?.let { osVersion -> resourceAttributesBuilder.put(AttributeKey.stringKey("os.version"), osVersion) }
        osArch?.let { osArch -> resourceAttributesBuilder.put(AttributeKey.stringKey("os.arch"), osArch) }

        val resource = Resource.create(resourceAttributesBuilder.build())
        return resource
    }

    private fun createExporters(): List<SpanExporter> = buildList {

        if (customSpanExporters.isEmpty()) {
            logger.debug { "No custom span exporters configured. Use log span exporter by default." }
            add(LoggingSpanExporter.create())
        }

        customSpanExporters.forEach { exporter ->
            logger.debug { "Adding span exporter: ${exporter::class.simpleName}" }
            add(exporter)
        }
    }

    private fun SdkTracerProviderBuilder.addProcessors(exporter: SpanExporter) {

        if (customSpanProcessorsCreator.isEmpty()) {
            logger.debug { "No custom span processors configured. Use batch span processor with ${exporter::class.simpleName} as an exporter." }
            addSpanProcessor(SimpleSpanProcessor.builder(exporter).build())
            return
        }

        customSpanProcessorsCreator.forEach { processorCreator ->
            val spanProcessor = processorCreator(exporter)
            logger.debug { "Adding span processor: ${spanProcessor::class.simpleName}" }
            addSpanProcessor(spanProcessor)
        }
    }

    //endregion Private Methods
}
