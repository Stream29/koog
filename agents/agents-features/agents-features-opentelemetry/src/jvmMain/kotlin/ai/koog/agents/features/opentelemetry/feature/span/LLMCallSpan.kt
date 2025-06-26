package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Tracer

class LLMCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
) : TraceSpanBase(tracer, parentSpan) {


}