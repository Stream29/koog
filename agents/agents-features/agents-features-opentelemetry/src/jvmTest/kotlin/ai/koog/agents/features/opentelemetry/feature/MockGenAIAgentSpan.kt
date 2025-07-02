package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.api.trace.StatusCode

internal class MockGenAIAgentSpan(
    override val spanId: String,
    parentSpan: GenAIAgentSpan? = null,
    private val endStatusCode: StatusCode = StatusCode.OK,
) : GenAIAgentSpan(parentSpan) {

    val isStarted: Boolean
        get() = (span as MockSpan).isStarted

    val isEnded: Boolean
        get() = (span as MockSpan).isEnded

    val currentStatus: StatusCode?
        get() = (span as MockSpan).status

    fun start() {
        startInternal(attributes = emptyList())
    }

    fun end() {
        endInternal(attributes = emptyList(), status = endStatusCode)
    }

    override val attributes: List<Attribute>
        get() = TODO("Not yet implemented")
}
