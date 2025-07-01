package ai.koog.agents.features.opentelemetry.feature.event

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.trace.data.EventData

internal class LLMCallEvent : EventData {

    override fun getName(): String? {
        TODO("Not yet implemented")
    }

    override fun getAttributes(): Attributes? {
        TODO("Not yet implemented")
    }

    override fun getEpochNanos(): Long {
        TODO("Not yet implemented")
    }

    override fun getTotalAttributeCount(): Int {
        TODO("Not yet implemented")
    }
}