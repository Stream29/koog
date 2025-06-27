package ai.koog.agents.features.opentelemetry.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.writer.FeatureMessageRemoteWriter
import io.opentelemetry.sdk.OpenTelemetrySdk

/**
 * TODO: SD -- ...
 */
public class OpenTelemetryRemoteMessageWriter(public val sdk: OpenTelemetrySdk) : FeatureMessageRemoteWriter() {

    private companion object {
        private const val OTLP_ENDPOINT = "http://localhost:4317"
    }

    override suspend fun initialize() {
    }

    override suspend fun processMessage(message: FeatureMessage) {
    }

    override suspend fun close() {
        sdk.close()
    }
}
