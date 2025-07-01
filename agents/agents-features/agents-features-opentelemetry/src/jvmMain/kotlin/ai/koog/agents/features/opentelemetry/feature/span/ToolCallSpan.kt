package ai.koog.agents.features.opentelemetry.feature.span

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.features.opentelemetry.feature.attribute.SpanAttribute
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
) : GenAIAgentSpan(tracer, parentSpan) {

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
            add(SpanAttribute.Operation.Name(SpanAttribute.Operation.OperationName.EXECUTE_TOOL))
            add(SpanAttribute.Conversation.Id(id = runId))
            add(SpanAttribute.Tool.Call.Id(id = Uuid.random().toString()))
            add(SpanAttribute.Tool.Name(name = tool.name))
            add(SpanAttribute.Tool.Description(description = tool.descriptor.description))
        }

        startInternal(kind = SpanKind.INTERNAL, attributes = attributes)
    }

    fun end(
        result: String,
        statusCode: StatusCode,
    ) {
        val attribute = listOf(
            SpanAttribute.Custom("gen_ai.tool.result", result),
        )

        endInternal(attribute, statusCode)
    }
}