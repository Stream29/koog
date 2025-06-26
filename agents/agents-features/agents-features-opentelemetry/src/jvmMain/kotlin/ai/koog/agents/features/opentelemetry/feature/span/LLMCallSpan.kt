package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

internal class LLMCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
//    val callId: String,
    val model: String,
    val temperature: Double,
    val promptId: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, sessionId: String, nodeName: String, promptId: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, sessionId, nodeName), promptId = promptId)

        private fun createIdFromParent(parentId: String, promptId: String): String =
            "$parentId.llm.$promptId"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, model)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.CHAT.id),
            GenAIAttribute.Request.Model(model),
//            GenAIAttribute.Request.Temperature(temperature),
        )
        start(attributes)
    }

    fun end(
        responses: List<Message.Response>,
        statusCode: StatusCode,
    ) {
        val attributes = listOf(
            GenAIAttribute.Response.Model(model),
//            GenAIAttribute.Response.Id(callId),
            GenAIAttribute.Custom("gen_ai.response.content", responses.map { it.content }),
        )

        end(attributes, statusCode)
    }
}