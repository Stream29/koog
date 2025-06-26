package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Tracer

internal class ToolCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
) : TraceSpanBase(tracer, parentSpan) {


}