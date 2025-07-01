package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.agents.features.opentelemetry.span.ExecuteToolSpan
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
        var id = CreateAgentSpan.Companion.createId(agentId)
        if (runId != null) {
            id = InvokeAgentSpan.Companion.createId(agentId, runId)
            if (nodeName != null) {
                id = NodeExecuteSpan.Companion.createId(agentId, runId, nodeName)
                if (toolName != null) {
                    id = ExecuteToolSpan.Companion.createId(agentId, runId, nodeName, toolName)
                    if (promptId != null) {
                        id = InferenceSpan.Companion.createId(agentId, runId, nodeName, promptId)
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
        val agentRunSpanId = InvokeAgentSpan.Companion.createId(agentId, runId)
        val agentSpanId = CreateAgentSpan.Companion.createId(agentId)

        endUnfinishedSpans(filter = { id -> id != agentSpanId && id != agentRunSpanId })
    }
}