package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.prompt.message.Message
import io.opentelemetry.api.trace.Tracer

internal class LLMCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
//    val callId: String,
    val model: String,
    val temperature: Double,
//    val prompt: String,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, sessionId: String, nodeName: String, model: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, sessionId, nodeName), model = model)

        private fun createIdFromParent(parentId: String, model: String): String =
            "$parentId.llm.$model"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, model)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.CHAT.id),
            GenAIAttribute.Request.Model(model),
            GenAIAttribute.Request.Temperature(temperature),
        )
        start(attributes)
    }

    fun end(response: Message.Response) {
        val attributes = listOf(
            GenAIAttribute.Response.Model(model),
//            GenAIAttribute.Response.Id(callId),
            GenAIAttribute.Custom("gen_ai.response.content", response.content),
        )
        end(emptyList())
    }
}