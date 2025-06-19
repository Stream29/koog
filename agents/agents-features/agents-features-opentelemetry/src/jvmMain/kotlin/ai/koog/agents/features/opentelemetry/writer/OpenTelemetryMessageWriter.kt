package ai.koog.agents.features.opentelemetry.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig
import ai.koog.agents.features.common.writer.FeatureMessageRemoteWriter
import io.opentelemetry.api.OpenTelemetry

public class OpenTelemetryMessageWriter(configuration: OpenTelemetryConfiguration) : FeatureMessageProcessor() {

    override suspend fun initialize() {

    }

    override suspend fun processMessage(message: FeatureMessage) {
    }

    override suspend fun close() {
    }

    //region Private Methods

    private fun initializeOpenTelemetry(): OpenTelemetry {
        val sdk: OpenTelemetrySdk =
            OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
                .setLoggerProvider(
                    SdkLoggerProvider.builder()
                        .setResource(
                            Resource.getDefault().toBuilder()
                                .put(SERVICE_NAME, "log4j-example")
                                .build())
                        .addLogRecordProcessor(
                            BatchLogRecordProcessor.builder(
                                    OtlpGrpcLogRecordExporter.builder()
                                        .setEndpoint("http://localhost:4317")
                                        .build())
                                .build())
                        .build())
                .build();

        // Add hook to close SDK, which flushes logs
        Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

        return sdk;
      }

    //endregion Private Methods
}
