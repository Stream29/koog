package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class LLMCallMultipleChoiceSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
    val model: LLModel,
    val temperature: Double,
    val promptId: String,
    val tools: List<ToolDescriptor>
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, promptId: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), promptId = promptId)

        private fun createIdFromParent(parentId: String, promptId: String): String =
            "$parentId.llm.$promptId"
    }

    override val spanId: String = createIdFromParent(parentId = parentSpan.spanId, promptId = promptId)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.CHAT.id),
            GenAIAttribute.Request.Model(model),
            GenAIAttribute.Request.Temperature(temperature),
            GenAIAttribute.Tool.Name(tool.name),
            GenAIAttribute.Tool.Description(tool.descriptor.description),
        )
        startInternal(attributes)
    }

    fun end(
        responses: List<Message.Response>,
        statusCode: StatusCode,
    ) {
        val attributes = listOf(
            GenAIAttribute.Response.Model(model),
            GenAIAttribute.Custom("gen_ai.response.content", responses.map { it.content }),
        )

        endInternal(attributes = attributes, status = statusCode)
    }
}