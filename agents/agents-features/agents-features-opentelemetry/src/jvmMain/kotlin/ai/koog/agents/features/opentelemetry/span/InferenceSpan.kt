package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.opentelemetry.api.trace.SpanKind

/**
 * LLM Call Span
 */
internal class InferenceSpan(
    parent: NodeExecuteSpan,
    provider: LLMProvider,
    runId: String,
    model: LLModel,
    temperature: Double,
    private val promptId: String,
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, promptId: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), promptId = promptId)

        private fun createIdFromParent(parentId: String, promptId: String): String =
            "$parentId.llm.$promptId"
    }

    override val spanId: String = createIdFromParent(parentId = parent.spanId, promptId = promptId)

    override val kind: SpanKind = SpanKind.CLIENT

    /**
     * Add the necessary attributes for the Inference Span according to the Open Telemetry Semantic Convention:
     * https://opentelemetry.io/docs/specs/semconv/gen-ai/gen-ai-spans/#inference
     *
     * Attribute description:
     * - gen_ai.operation.name (required)
     * - gen_ai.system (required)
     * - error.type (conditional)
     * - gen_ai.conversation.id (conditional)
     * - gen_ai.output.type (conditional/required)
     * - gen_ai.request.choice.count (conditional/required)
     * - gen_ai.request.model (conditional/required)
     * - gen_ai.request.seed (conditional/required)
     * - server.port (conditional/required)
     * - gen_ai.request.frequency_penalty (recommended)
     * - gen_ai.request.max_tokens (recommended)
     * - gen_ai.request.presence_penalty (recommended)
     * - gen_ai.request.stop_sequences (recommended)
     * - gen_ai.request.temperature (recommended)
     * - gen_ai.request.top_k (recommended)
     * - gen_ai.request.top_p (recommended)
     * - gen_ai.response.finish_reasons (recommended)
     * - gen_ai.response.id (recommended)
     * - gen_ai.response.model (recommended)
     * - gen_ai.usage.input_tokens (recommended)
     * - gen_ai.usage.output_tokens (recommended)
     * - server.address (recommended)
     */
    init {
        // gen_ai.operation.name
        addAttribute(SpanAttributes.Operation.Name(SpanAttributes.Operation.OperationNameType.CHAT))

        // gen_ai.system
        addAttribute(CommonAttributes.System(provider))

        // gen_ai.conversation.id
        addAttribute(SpanAttributes.Conversation.Id(runId))

        // gen_ai.request.model
        addAttribute(SpanAttributes.Request.Model(model))

        // gen_ai.request.temperature
        addAttribute(SpanAttributes.Request.Temperature(temperature))
    }
}
