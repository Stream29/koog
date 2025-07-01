package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.api.trace.SpanKind

/**
 * Root Agent Span
 */
internal class CreateAgentSpan(
    private val provider: LLMProvider,
    private val agentId: String,
    private val model: LLModel,
) : GenAIAgentSpan(null) {

    companion object {
        fun createId(agentId: String): String = "agent.$agentId"
    }

    override val spanId = createId(agentId)

    override val kind: SpanKind = SpanKind.CLIENT

    /**
     * Add the necessary attributes for the Create Agent Span according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#create-agent-span
     *
     * Attribute description:
     * - gen_ai.operation.name (required)
     * - gen_ai.system (required)
     * - error.type (conditional)
     * - gen_ai.agent.description (conditional)
     * - gen_ai.agent.id (conditional)
     * - gen_ai.agent.name (conditional)
     * - gen_ai.request.model (conditional)
     * - gen_ai.server.port (conditional)
     * - gen_ai.server.address (recommended)
     */
    override val attributes: List<GenAIAttribute> = buildList {
        // gen_ai.operation.name
        add(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationName.CREATE_AGENT))

        // gen_ai.system
        add(CommonAttributes.System(provider))

        // gen_ai.agent.id
        add(SpanAttributes.Agent.Id(agentId))

        // gen_ai.request.model
        add(SpanAttributes.Request.Model(model))
    }
}
