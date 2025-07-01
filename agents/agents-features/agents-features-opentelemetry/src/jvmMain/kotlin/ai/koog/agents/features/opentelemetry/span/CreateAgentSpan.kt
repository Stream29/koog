package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLMProvider
import io.opentelemetry.api.trace.SpanKind

/**
 * Root Agent Span
 */
internal class CreateAgentSpan(
    private val provider: LLMProvider,
    private val agentId: String,
) : GenAIAgentSpan(null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    override val spanId = createId(agentId)

    override val kind: SpanKind = SpanKind.CLIENT

    override val attributes: List<GenAIAttribute> = buildList {
        // operation.name
        add(GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.CREATE_AGENT))

        // system
        add(GenAIAttribute.System(provider))

        // error.type (conditional)
        add(GenAIAttribute.Agent.Id(agentId))
    }
}
