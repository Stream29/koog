package ai.koog.agents.features.tracing.writer

import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor

public class OpenTelemetryMessageWriter : FeatureMessageProcessor() {

    override suspend fun processMessage(message: FeatureMessage) {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}
