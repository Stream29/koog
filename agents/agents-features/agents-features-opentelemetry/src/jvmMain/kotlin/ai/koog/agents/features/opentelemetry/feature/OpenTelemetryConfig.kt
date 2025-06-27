package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.common.config.FeatureConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Configuration class for OpenTelemetry integration.
 *
 * Provides seamless integration with the OpenTelemetry SDK, allowing initialization
 * and customization of various components such as the tracer, meter, exporters, etc.
 */
public class OpenTelemetryConfig : FeatureConfig() {

    private companion object {
        private val osName = System.getProperty("os.name")
        private val osVersion = System.getProperty("os.version")
        private val osArch = System.getProperty("os.arch")

        // Local server endpoints
        private const val OTLP_ENDPOINT = "http://localhost:4317"
    }

    private val productProperties = run {
        val props = Properties()
        this::class.java.classLoader.getResourceAsStream("product.properties")?.use { stream ->
            props.load(stream)
        }
        props
    }

    /**
     * Specifies the name of the service used for OpenTelemetry configuration.
     */
    public var otelServiceName: String = productProperties.getProperty("product.name") ?: "ai.koog"

    /**
     * Represents the version identifier for the service in the OpenTelemetry configuration.
     */
    public var otelServiceVersion: String = productProperties.getProperty("product.name") ?: ""

    /**
     * The namespace to which the service belongs in the OpenTelemetry configuration.
     * This optional property can be used to logically group services under a common namespace.
     */
    public var otelServiceNamespace: String? = null


    private var _instrumentationScopeName: String = otelServiceName

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
            ?: initializeOpenTelemetry(otelServiceName, otelServiceVersion, otelServiceNamespace)
                .also {
                    _sdk = it
                    _instrumentationScopeName = otelServiceName
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

        // Resource
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

        // Create exporters for local observability
        val exporters = createExporters()

        // Trace Provider with exporters
        val traceProviderBuilder = SdkTracerProvider.builder()
            .setSampler(Sampler.alwaysOn())
            .setResource(resource)

        // Add span processors for each exporter
        exporters.forEach { exporter ->
            traceProviderBuilder.addSpanProcessor(
                BatchSpanProcessor.builder(exporter).build()
            )
        }

        val sdk = builder
            .setTracerProvider(traceProviderBuilder.build())
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()

//        val sdk =
//            OpenTelemetrySdk.builder()
//                .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
//                .setLoggerProvider(
//                    SdkLoggerProvider.builder()
//                        .setResource(
//                            Resource.getDefault().toBuilder()
//                                .put(SERVICE_NAME, "log4j-example")
//                                .build())
//                        .addLogRecordProcessor(
//                            BatchLogRecordProcessor.builder(
//                                    OtlpGrpcLogRecordExporter.builder()
//                                        .setEndpoint("http://localhost:4317")
//                                        .build())
//                                .build())
//                        .build())
//                .build()

        // Add a hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(Thread(sdk::close))

        return sdk
    }

    private fun createExporters(): List<SpanExporter> = buildList {
//        // Always add logging exporter for console output
//        add(LoggingSpanExporter.create())

        println("✅ Console logging exporter configured")

//        // Try to add Jaeger exporter
//        try {
//            add(JaegerGrpcSpanExporter.builder()
//                .setEndpoint(JAEGER_ENDPOINT)
//                .build())
//            println("✅ Jaeger exporter configured at $JAEGER_ENDPOINT")
//            println("   Access Jaeger UI at: http://localhost:16686")
//        } catch (e: Exception) {
//            println("⚠️ Jaeger exporter failed: ${e.message}")
//            println("   To enable Jaeger, run: ./gradlew :agents:agents-features:agents-features-opentelemetry:run")
//        }

        // Try to add OTLP exporter
        try {
            add(OtlpGrpcSpanExporter.builder()
                .setEndpoint(OTLP_ENDPOINT)
                .build())
            println("✅ OTLP exporter configured at $OTLP_ENDPOINT")
        } catch (e: Exception) {
            println("⚠️ OTLP exporter failed: ${e.message}")
            println("   To enable OTLP exporter, run: ./gradlew :agents:agents-features:agents-features-opentelemetry:run")
        }
    }

    //endregion Private Methods
}
