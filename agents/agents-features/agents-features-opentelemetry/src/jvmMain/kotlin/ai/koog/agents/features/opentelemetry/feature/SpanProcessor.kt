package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import io.opentelemetry.api.trace.Tracer

internal class SpanProcessor(private val tracer: Tracer) {

    fun startSpan(
        span: GenAIAgentSpan
    ) {
        tracer.spanBuilder(span.name).startSpan()
    }
}