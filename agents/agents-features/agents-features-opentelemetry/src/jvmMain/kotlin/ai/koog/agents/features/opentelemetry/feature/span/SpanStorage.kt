package ai.koog.agents.features.opentelemetry.feature.span

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.ConcurrentHashMap

internal class SpanStorage() {

    companion object {
        private val logger = KotlinLogging.logger {  }
    }

    private val spans = ConcurrentHashMap<String, GenAIAgentSpan>()

    val size: Int
        get() = spans.size

    fun addSpan(id: String, span: GenAIAgentSpan) {
        spans[id] = span
    }

    inline fun <reified T>getSpan(id: String): T? where T : GenAIAgentSpan {
        return spans[id] as? T
    }

    inline fun <reified T>getSpanOrThrow(id: String): T where T : GenAIAgentSpan {
        val span = spans[id] ?: error("Span with id: $id not found")
        return span as? T
            ?: error("Span with id <$id> is not of expected type. Expected: <${T::class.simpleName}>, actual: <${span::class.simpleName}>")
    }

    inline fun <reified T>getOrPutSpan(id: String, create: () -> T): T where T : GenAIAgentSpan {
        return spans.getOrPut(id, create) as T
    }

    inline fun <reified T>removeSpan(id: String): T? where T : GenAIAgentSpan {
        return spans.remove(id) as? T
    }

    fun findTopMostSpan(
        agentId: String,
        runId: String? = null,
        nodeName: String? = null,
        toolName: String? = null,
        promptId: String? = null,
    ): GenAIAgentSpan? {
        var id = AgentSpan.createId(agentId)
        if (runId != null) {
            id = AgentRunSpan.createId(agentId, runId)
            if (nodeName != null) {
                id = NodeExecuteSpan.createId(agentId, runId, nodeName)
                if (toolName != null) {
                    id = ToolCallSpan.createId(agentId, runId, nodeName, toolName)
                    if (promptId != null) {
                        id = LLMCallSpan.createId(agentId, runId, nodeName, promptId)
                    }
                }
            }
        }

        return getSpan(id)
    }

    fun endUnfinishedSpans(filter: (spanId: String) -> Boolean = { true }) {
        spans.entries
            .filter { (id, _) ->
                val isRequireFinish = filter(id)
                isRequireFinish
            }
            .forEach { (id, span) ->
                logger.warn { "Force close span with id: $id" }
                span.endInternal(attributes = emptyList(), StatusCode.UNSET)
            }
    }

    fun endUnfinishedAgentRunSpans(agentId: String, runId: String) {
        val agentRunSpanId = AgentRunSpan.createId(agentId, runId)
        val agentSpanId = AgentSpan.createId(agentId)

        endUnfinishedSpans(filter = { id -> id != agentSpanId && id != agentRunSpanId })
    }
}
