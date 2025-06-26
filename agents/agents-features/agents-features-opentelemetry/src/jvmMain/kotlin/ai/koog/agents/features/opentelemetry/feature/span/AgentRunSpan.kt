package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

internal class AgentRunSpan(
    tracer: Tracer,
    spanId: String,
    parentContext: Context,
    agentId: String,
    sessionId: String,
    strategyName: String,
) : TraceSpanBase(tracer, spanId, parentContext) {

    private var _span: Span? = null

    override val span: Span
        get() = _span ?: error("Span is not initialized")

    override val attributes: Array<GenAIAttribute> = arrayOf(
        GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT.id),
        GenAIAttribute.Agent.Id(agentId),
        GenAIAttribute.Custom("gen_ai.agent.sessionId", sessionId),
        GenAIAttribute.Custom("gen_ai.agent.strategy", strategyName),
        GenAIAttribute.Custom("gen_ai.agent.completed", false),
    )

    override fun create() {

        val builder = tracer.spanBuilder(spanId)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setParent(parentContext)






            .setAllAttributes()
            .startSpan()
            .also { _span = it }
    }

    fun end() {

    }
}
