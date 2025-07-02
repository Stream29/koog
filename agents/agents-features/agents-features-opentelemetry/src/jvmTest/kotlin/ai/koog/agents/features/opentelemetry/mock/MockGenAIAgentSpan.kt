package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.api.trace.StatusCode

internal class MockGenAIAgentSpan(
    override val spanId: String,
    parent: GenAIAgentSpan? = null,
    override val attributes: List<Attribute> = emptyList(),
    private val spanEndStatus: SpanEndStatus? = null,
) : GenAIAgentSpan(parent) {

    val isStarted: Boolean
        get() = (span as MockSpan).isStarted

    val isEnded: Boolean
        get() = (span as MockSpan).isEnded

    val currentStatus: StatusCode?
        get() = (span as MockSpan).status
}
