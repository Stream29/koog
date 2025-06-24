package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.common.config.FeatureConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import io.opentelemetry.sdk.trace.samplers.Sampler
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * TODO: SD --
 */
public class OpenTelemetryConfig : FeatureConfig() {

    private companion object {
        private val osName = System.getProperty("os.name")
        private val osVersion = System.getProperty("os.version")
        private val osArch = System.getProperty("os.arch")

        // Local server endpoints
        private const val JAEGER_ENDPOINT = "http://localhost:14250"
        private const val OTLP_ENDPOINT = "http://localhost:4317"

    }

    init {
        initializeOpenTelemetry("ai-koog-agent", "0.2.1", "development").also { _sdk = it }
    }

    private var _sdk: OpenTelemetrySdk? = null

    /**
     * TODO: SD -- ...
     */
    public val sdk: OpenTelemetrySdk
        get() = _sdk ?: error("OpenTelemetry SDK is not initialized")

    /**
     * TODO: SD -- ...
     */
    public val meter: Meter
        get() = sdk.getMeter("ai-koog-agent")

    /**
     * TODO: SD -- ...
     */
    public val tracer: Tracer
        get() = sdk.getTracer("ai-koog-agent")

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
        // Always add logging exporter for console output
        add(LoggingSpanExporter.create())
        println("✅ Console logging exporter configured")

        // Try to add Jaeger exporter
        try {
            add(JaegerGrpcSpanExporter.builder()
                .setEndpoint(JAEGER_ENDPOINT)
                .build())
            println("✅ Jaeger exporter configured at $JAEGER_ENDPOINT")
            println("   Access Jaeger UI at: http://localhost:16686")
        } catch (e: Exception) {
            println("⚠️ Jaeger exporter failed: ${e.message}")
            println("   To enable Jaeger, run: ./gradlew :agents:agents-features:agents-features-opentelemetry:run")
        }

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
