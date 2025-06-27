package ai.koog.agents.features.opentelemetry.feature.span

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.ConcurrentHashMap

internal class SpanStorage() {

    companion object {
        private val logger = KotlinLogging.logger {  }
    }

    private val spans = ConcurrentHashMap<String, TraceSpanBase>()

    val size: Int
        get() = spans.size

    fun addSpan(id: String, span: TraceSpanBase) {
        spans[id] = span
    }

    inline fun <reified T>getSpan(id: String): T? where T : TraceSpanBase {
        return spans[id] as? T
    }

    inline fun <reified T>getSpanOrThrow(id: String): T where T : TraceSpanBase {
        val span = spans[id] ?: error("Span with id: $id not found")
        return span as? T
            ?: error("Span with id: $id is not of expected type. Expected: ${T::class.simpleName}, actual: ${span::class.simpleName}")
    }

    inline fun <reified T>getOrPutSpan(id: String, create: () -> T): T where T : TraceSpanBase {
        return spans.getOrPut(id, create) as T
    }

    inline fun <reified T>removeSpan(id: String): T? where T : TraceSpanBase {
        return spans.remove(id) as? T
    }

    fun findTopMostSpan(
        agentId: String,
        sessionId: String? = null,
        nodeName: String? = null,
        toolName: String? = null,
        promptId: String? = null,
    ): TraceSpanBase? {
        var id = AgentSpan.createId(agentId)
        if (sessionId != null) {
            id = AgentRunSpan.createId(agentId, sessionId)
            if (nodeName != null) {
                id = NodeExecuteSpan.createId(agentId, sessionId, nodeName)
                if (toolName != null) {
                    id = ToolCallSpan.createId(agentId, sessionId, nodeName, toolName)
                    if (promptId != null) {
                        id = LLMCallSpan.createId(agentId, sessionId, nodeName, promptId)
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
                println("SD -- Span to be finished: $id, process: $isRequireFinish")
                isRequireFinish
            }
            .forEach { (id, span) ->
                println("SD -- Closing unfinished span: $id")
                logger.warn { "Force close span with id: $id" }
                span.endInternal(attributes = emptyList(), StatusCode.UNSET)
            }
    }

    fun endUnfinishedAgentRunSpans(agentId: String, sessionId: String) {
        println("SD -- Agent run finish. Force closing all unfinished spans")
        val agentRunSpanId = AgentRunSpan.createId(agentId, sessionId)
        val agentSpanId = AgentSpan.createId(agentId)

        endUnfinishedSpans(filter = { id -> id != agentSpanId && id != agentRunSpanId })
    }
}
