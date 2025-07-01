package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class ToolCallSpan(
    tracer: Tracer,
    parentSpan: NodeExecuteSpan,
    private val runId: String,
    private val tool: Tool<*, *>,
    private val toolArgs: ToolArgs,
) : TraceSpanBase(tracer, parentSpan) {

    companion object {
        fun createId(agentId: String, runId: String, nodeName: String, toolName: String): String =
            createIdFromParent(parentId = NodeExecuteSpan.createId(agentId, runId, nodeName), toolName = toolName)

        private fun createIdFromParent(parentId: String, toolName: String): String =
            "$parentId.tool.$toolName"
    }

    override val spanId: String = createIdFromParent(parentSpan.spanId, tool.name)

    @ExperimentalUuidApi
    fun start() {
        val attributes = buildList {
            add(GenAIAttribute.Operation.Name(GenAIAttribute.Operation.OperationName.EXECUTE_TOOL.id))
            add(GenAIAttribute.Conversation.Id(id = runId))
            add(GenAIAttribute.Tool.Call.Id(id = Uuid.random().toString()))
            add(GenAIAttribute.Tool.Name(name = tool.name))
            add(GenAIAttribute.Tool.Description(description = tool.descriptor.description))
        }

        startInternal(kind = SpanKind.INTERNAL, attributes = attributes)
    }

    fun end(
        result: String,
        statusCode: StatusCode,
    ) {
        val attribute = listOf(
            GenAIAttribute.Custom("gen_ai.tool.result", result),
        )

        endInternal(attribute, statusCode)
    }
}