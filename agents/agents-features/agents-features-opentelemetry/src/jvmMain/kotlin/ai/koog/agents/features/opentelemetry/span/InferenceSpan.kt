package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode

/**
 * LLM Call Span
 */
internal class InferenceSpan(
    parent: NodeExecuteSpan,
    private val runId: String,
    private val model: LLModel,
    private val temperature: Double,
    private val promptId: String,
    private val tools: List<ToolDescriptor>
) : GenAIAgentSpan(parent) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, promptId: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), promptId = promptId)

        private fun createIdFromParent(parentId: String, promptId: String): String =
            "$parentId.llm.$promptId"
    }

    override val spanId: String = createIdFromParent(parentId = parent.spanId, promptId = promptId)

    override val kind: SpanKind = SpanKind.CLIENT

    fun start() {
        val attributes = buildList {
            add(GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.CHAT))
            add(GenAIAttribute.Conversation.Id(runId))
            add(GenAIAttribute.Request.Model(model))
            add(GenAIAttribute.Request.Temperature(temperature))

            if (tools.isNotEmpty()) {
                add(GenAIAttribute.Request.Tools(tools))
            }
        }

        startInternal(attributes = attributes)
    }

    fun end(
        responses: List<Message.Response>,
        statusCode: StatusCode,
    ) {

        ...
        // TODO: SD -- fix


        val attributes = buildList {
            add(SpanAttribute.Response.Model(model))
            add(SpanAttribute.Custom("gen_ai.response.content", responses.map { it.content }))
        }



        endInternal(attributes = attributes, status = statusCode)
    }
}