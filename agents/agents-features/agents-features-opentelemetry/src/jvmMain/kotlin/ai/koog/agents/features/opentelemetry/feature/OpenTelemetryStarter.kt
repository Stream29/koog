package ai.koog.agents.features.opentelemetry.feature

import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * TODO: SD -- delete
 */
public object OpenTelemetryStarter {

    @OptIn(ExperimentalUuidApi::class)
    private val resource = Resource.builder()
        .put("service.name", "koog-agent")
        .put("service.instance.id", Uuid.random().toString())
        .build()

    /**
     * TODO: SD -- delete
     */
    public fun create(): OpenTelemetrySdk = OpenTelemetrySdk.builder()
        .setTracerProvider(createTracerConfig())
        .setLoggerProvider(createLoggerProvider())
        .build();


    /**
     * TODO: SD -- delete
     */
    public fun createLoggerProvider(): SdkLoggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(
            BatchLogRecordProcessor.builder(
                LogRecordExporter.composite(
//                        OtlpGrpcLogRecordExporter.builder()
//                            .setEndpoint("http://localhost:4317")
//                            .setTimeout(2, TimeUnit.SECONDS)
//                            .build(),
                    SystemOutLogRecordExporter.create()
                )
            ).build()
        ).build()

    /**
     * TODO: SD -- delete
     */
    public fun createTracerConfig(): SdkTracerProvider {
        val otlpExporter = OtlpGrpcSpanExporter.builder()
            .setEndpoint("http://localhost:4317")
            .setTimeout(2, TimeUnit.SECONDS)
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .setResource(Resource.getDefault().merge(resource))
            .addSpanProcessor(BatchSpanProcessor.builder(otlpExporter).build())
            .build()

        Runtime.getRuntime().addShutdownHook(Thread(tracerProvider::close))

        return tracerProvider
    }

}