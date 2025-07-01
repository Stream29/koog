package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode

/**
 * Agent Run Span
 */
internal class InvokeAgentSpan(
    parent: CreateAgentSpan,
    private val runId: String,
    private val agentId: String,
    private val strategyName: String,
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String): String =
            createIdFromParent(parentId = CreateAgentSpan.createId(agentId), runId = runId)

        private fun createIdFromParent(parentId: String, runId: String): String =
            "$parentId.run.$runId"
    }

    override val spanId: String = createIdFromParent(parent.spanId, runId)

    /**
     * Add the necessary attributes for the Invoke Agent Span according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-agent-spans/#invoke-agent-span
     *
     * Attribute description:
     * - gen_ai.operation.name (required)
     * - gen_ai.system (required)
     * - error.type (conditional)
     * - gen_ai.agent.description (conditional)
     * - gen_ai.agent.id (conditional)
     * - gen_ai.agent.name (conditional)
     *
     * - gen_ai.conversation.id
     * - gen_ai.request.model (conditional)
     * - gen_ai.server.port (conditional)
     * - gen_ai.server.address (recommended)
     */
    override val attributes: List<GenAIAttribute> = buildList {

    }

    fun start(): Span {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.INVOKE_AGENT),
            GenAIAttribute.Agent.Id(agentId),
            GenAIAttribute.Conversation.Id(runId),
            GenAIAttribute.CustomAttribute("gen_ai.agent.strategy", strategyName),
            GenAIAttribute.CustomAttribute("gen_ai.agent.completed", false),
        )

        return startInternal(kind = SpanKind.CLIENT, attributes = attributes)
    }

    fun end(
        completed: Boolean,
        result: String,
        statusCode: StatusCode,
    ) {
        val attributes = listOf(
            GenAIAttribute.CustomAttribute("gen_ai.agent.result", result),
            GenAIAttribute.CustomAttribute("gen_ai.agent.completed", completed),
        )

        endInternal(attributes, statusCode)
    }
}
