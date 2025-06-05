package ai.koog.agents.features.opentelemetry.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.writer.FeatureMessageRemoteWriter
import io.opentelemetry.sdk.OpenTelemetrySdk

/**
 * TODO: SD -- ...
 */
public class OpenTelemetryRemoteMessageWriter(public val sdk: OpenTelemetrySdk) : FeatureMessageRemoteWriter() {

    override suspend fun initialize() {
    }

    override suspend fun processMessage(message: FeatureMessage) {
    }

    override suspend fun close() {
        // TODO: SD -- clean up
        sdk.close()
//        // Add a hook to close SDK, which flushes logs
//        Runtime.getRuntime().addShutdownHook(Thread(sdk::close))

    }
}
