package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.agents.features.opentelemetry.feature.attribute.SpanAttribute
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class LLMCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
    private val runId: String,
    private val model: LLModel,
    private val temperature: Double,
    private val promptId: String,
    private val tools: List<ToolDescriptor>
) : GenAIAgentSpan(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, promptId: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), promptId = promptId)

        private fun createIdFromParent(parentId: String, promptId: String): String =
            "$parentId.llm.$promptId"
    }

    override val spanId: String = createIdFromParent(parentId = parentSpan.spanId, promptId = promptId)

    fun start() {
        val attributes = buildList {
            add(GenAIAttribute.Operation.Name(SpanAttribute.Operation.OperationName.CHAT))
            add(SpanAttribute.Conversation.Id(runId))
            add(SpanAttribute.Request.Model(model))
            add(SpanAttribute.Request.Temperature(temperature))

            if (tools.isNotEmpty()) {
                add(GenAIAttribute.Request.Tools(tools))
            }
        }

        startInternal(kind = SpanKind.CLIENT, attributes = attributes)
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