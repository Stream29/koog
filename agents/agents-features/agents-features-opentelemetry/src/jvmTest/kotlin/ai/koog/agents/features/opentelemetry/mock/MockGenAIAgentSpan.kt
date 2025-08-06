package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanEndStatus
import io.opentelemetry.api.trace.StatusCode

internal class MockGenAIAgentSpan(
    override val spanId: String,
    parent: GenAIAgentSpan? = null,
    private val spanEndStatus: SpanEndStatus? = null,
) : GenAIAgentSpan(parent) {

    val isStarted: Boolean
        get() = (span as MockSpan).isStarted

    val isEnded: Boolean
        get() = (span as MockSpan).isEnded

    val currentStatus: StatusCode?
        get() = (span as MockSpan).status
}
