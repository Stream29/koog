package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.StatusCode

internal class MockTraceSpan(
    override val spanId: String,
    parentSpan: TraceSpanBase? = null,
    private val endStatusCode: StatusCode = StatusCode.OK,
) : TraceSpanBase(MockTracer(), parentSpan) {

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
}
