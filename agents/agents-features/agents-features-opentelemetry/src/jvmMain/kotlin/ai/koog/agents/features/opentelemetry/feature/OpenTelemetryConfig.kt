package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.features.opentelemetry.attribute.addAttributes
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
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
import javax.swing.UIManager.put

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

    private val customResourceAttributes = mutableMapOf<AttributeKey<*>, Any>()

    private var _sdk: OpenTelemetrySdk? = null

    private var _serviceName: String = productProperties.getProperty("product.name") ?: "ai.koog"

    private var _serviceVersion: String = productProperties.getProperty("product.name") ?: ""

    private var _serviceNamespace: String? = null

    private var _instrumentationScopeName: String = _serviceName

    private var _instrumentationScopeVersion: String = _serviceVersion

    private var _sampler: Sampler? = null

    private var _verbose: Boolean = false

    /**
     * Indicates whether verbose telemetry data is enabled.
     *
     * When this value is `true`, the application collects more detailed telemetry data.
     * This setting is useful for debugging and detailed monitoring but may result in
     * increased resource usage or performance overhead.
     *
     * The value reflects the setting controlled through the `setVerbose(verbose: Boolean)` method,
     * with a default value of `false` if not explicitly configured.
     */
    public val isVerbose: Boolean
        get() = _verbose

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
        get() {
            return _sdk ?: initializeOpenTelemetry().also { sdk ->
                _sdk = sdk

                // Set the instrumentation scope name only once when SDK is created
                _instrumentationScopeName = _serviceName
                _instrumentationScopeVersion = _serviceVersion
            }
        }

    /**
     * Provides access to the `Tracer` instance for tracking and recording tracing data.
     */
    public val tracer: Tracer
        get() = sdk.getTracer(_instrumentationScopeName,_instrumentationScopeVersion)

    /**
     * The name of the service associated with this OpenTelemetry configuration.
     */
    public val serviceName: String
        get() = _serviceName

    /**
     * The version of the service used in the OpenTelemetry configuration.
     */
    public val serviceVersion: String
        get() = _serviceVersion

    /**
     * Represents the optional namespace for the service in the OpenTelemetry configuration.
     */
    public val serviceNamespace: String?
        get() = _serviceNamespace

    /**
     * Sets the service information for the OpenTelemetry configuration.
     * This information is used to identify the service in telemetry data.
     *
     * @param serviceName The name of the service.
     * @param serviceVersion The version of the service.
     * @param serviceNamespace An optional namespace for the service. Defaults to null if not provided.
     */
    public fun setServiceInfo(serviceName: String, serviceVersion: String, serviceNamespace: String? = null) {
        _serviceName = serviceName
        _serviceVersion = serviceVersion
        _serviceNamespace = serviceNamespace
    }

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
     * Adds a [SpanProcessor] creator function to the OpenTelemetry configuration.
     *
     * @param processor A function that takes a SpanExporter and returns the [SpanProcessor].
     *                        This allows defining custom logic for processing spans before they are exported.
     */
    public fun addSpanProcessor(processor: (SpanExporter) -> SpanProcessor) {
        customSpanProcessorsCreator.add(processor)
    }

    /**
     * Adds resource attributes to the OpenTelemetry configuration.
     * Resource attributes are key-value pairs that provide metadata
     * describing the entity producing telemetry data.
     *
     * @param attributes A map where the keys are of type [AttributeKey]
     *                   and the values are of type T. These attributes
     *                   will be added to the resource.
     * @param T The type of the values in the attribute map, which must be non-null.
     */
    public fun <T>addResourceAttributes(attributes: Map<AttributeKey<T>, T>) where T : Any {
        customResourceAttributes.putAll(attributes)
    }

    /**
     * Sets the sampler to be used by the OpenTelemetry configuration.
     * The sampler determines which spans are sampled and exported during application execution.
     *
     * @param sampler The sampler instance to set for the OpenTelemetry configuration.
     */
    public fun setSampler(sampler: Sampler) {
        _sampler = sampler
    }

    /**
     * Controls whether verbose telemetry data should be captured during application execution.
     * When set to `true`, the application collects more detailed telemetry data.
     * This option can be useful for debugging and fine-grained monitoring but may impact performance.
     *
     * Default value is `false`, meaning verbose data capture is disabled.
     */
    public fun setVerbose(verbose: Boolean) {
        _verbose = verbose
    }

    //region Private Methods

    private fun initializeOpenTelemetry(): OpenTelemetrySdk {

        // SDK
        val builder = OpenTelemetrySdk.builder()

        // Tracing
        val sampler = createSampler()
        val resource = createResources()
        val exporters: List<SpanExporter> = createExporters()

        val traceProviderBuilder = SdkTracerProvider.builder()
            .setSampler(sampler)
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

    private fun createSampler(): Sampler {
        return _sampler ?: Sampler.alwaysOn()
    }

    private fun createResources(): Resource {

        val defaultResourceAttributes: Map<AttributeKey<*>, String> = buildMap {
            put(AttributeKey.stringKey("service.name"), _serviceName)
            put(AttributeKey.stringKey("service.version"), _serviceVersion)
            put(AttributeKey.stringKey("service.namespace"), _serviceNamespace)
            put(AttributeKey.stringKey("service.instance.time"), DateTimeFormatter.ISO_INSTANT.format(Instant.now()))

            osName?.let { osName -> put(AttributeKey.stringKey("os.type"), osName) }
            osVersion?.let { osVersion -> put(AttributeKey.stringKey("os.version"), osVersion) }
            osArch?.let { osArch -> put(AttributeKey.stringKey("os.arch"), osArch) }
        }

        val resourceAttributesBuilder = Attributes.builder()
        resourceAttributesBuilder.addAttributes(defaultResourceAttributes)
        resourceAttributesBuilder.addAttributes(customResourceAttributes)

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
