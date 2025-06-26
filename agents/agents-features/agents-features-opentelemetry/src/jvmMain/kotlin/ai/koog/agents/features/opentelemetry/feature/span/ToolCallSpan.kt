package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import io.opentelemetry.api.trace.Tracer

internal class ToolCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
//    val callId: String,
    val tool: ToolDescriptor,
    val toolArgs: ToolArgs,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, sessionId: String, nodeName: String, toolName: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, sessionId, nodeName), toolName = toolName)

        private fun createIdFromParent(parentId: String, toolName: String): String =
            "$parentId.tool.$toolName"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, tool.name)

    fun start() {
        val attributes = listOf(
            GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.EXECUTE_TOOL.id),
            GenAIAttribute.Tool.Name(tool.name),
            GenAIAttribute.Tool.Description(tool.description),
        )
        start(attributes)
    }

    fun end(
        result: String
    ) {
        val attribute = listOf(
            GenAIAttribute.Custom("gen_ai.tool.result", result),
        )
        end(attribute)
    }
}