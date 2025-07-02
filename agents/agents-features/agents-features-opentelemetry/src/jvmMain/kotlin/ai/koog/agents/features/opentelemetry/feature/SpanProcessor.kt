package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

internal class SpanProcessor(private val tracer: Tracer) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _spans = ConcurrentHashMap<String, GenAIAgentSpan>()

    val spansCount: Int
        get() = _spans.count()







    // TODO: SD -- Remove
//    fun addEventsToSpan(spanId: String, message: Message, provider: LLMProvider, verbose: Boolean) {
//
//        val span = _spans[spanId] ?: return
//
//        val events = buildList {
//            when (message) {
//                is Message.User -> add(UserMessageEvent(provider, message, verbose))
//                is Message.System -> add(SystemMessageEvent(provider, message, verbose))
//                is Message.Assistant -> add(AssistantMessageEvent(provider, message, verbose))
//                is Message.Tool.Result -> add(ToolMessageEvent(provider, message, verbose))
//                else -> {}
//            }
//        }
//
//        span.addEvents(events)
//    }


    fun startSpan(
        span: GenAIAgentSpan,
        instant: Instant? = null,
    ) {

        logger.debug { "Starting span (name: ${span.name}, id: ${span.spanId})" }

        val spanKind = span.kind
        val parentContext = span.parent?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(span.name)
            .setStartTimestamp(instant ?: Instant.now())
            .setSpanKind(spanKind)
            .setParent(parentContext)

        span.attributes.forEach { attribute ->
            spanBuilder.setAttribute(attribute)
        }

        val startedSpan = spanBuilder.startSpan()

        // Store newly started span
        addSpan(span)

        // Update span context and span properties
        span.setSpan(startedSpan)
        span.setContext(startedSpan.storeInContext(parentContext))

        logger.debug { "Span has been started (name: ${span.name}, id: ${span.spanId})" }
    }

    fun endSpan(
        spanId: String,
        attributes: List<Attribute> = emptyList(),
        spanEndStatus: SpanEndStatus? = null
    ) {
        logger.debug { "Finishing the span (id: $spanId)" }

        val existingSpan = _spans[spanId]
            ?: error("Span with id '$spanId' not found. Make sure span was started or was not finished previously")

        logger.debug { "Finishing the span (name: $${existingSpan.name}, id: ${existingSpan.spanId})" }

        val spanToFinish = existingSpan.span

        attributes.forEach { attribute -> spanToFinish.setAttribute(attribute) }

        spanToFinish.setStatus(spanEndStatus)
        spanToFinish.end()

        removeSpan(existingSpan.spanId)
    }

    private fun Span.setStatus(endStatus: SpanEndStatus? = null) {
        val statusCode = endStatus?.code ?: StatusCode.OK
        val statusDescription = endStatus?.description ?: ""
        this.setStatus(statusCode, statusDescription)
    }












    fun endUnfinishedSpans(filter: (spanId: String) -> Boolean = { true }) {
        _spans.keys
            .filter { spanId ->
                val isRequireFinish = filter(spanId)
                isRequireFinish
            }
            .forEach { spanId ->
                logger.warn { "Force close span with id: $spanId" }
                endSpan(
                    spanId = spanId,
                    attributes = emptyList(),
                    spanEndStatus = SpanEndStatus(StatusCode.UNSET)
                )
            }
    }

    fun endUnfinishedInvokeAgentSpans(agentId: String, runId: String) {
        val agentRunSpanId = InvokeAgentSpan.createId(agentId, runId)
        val agentSpanId = CreateAgentSpan.createId(agentId)

        endUnfinishedSpans(filter = { id -> id != agentSpanId && id != agentRunSpanId })
    }



    //region Add/Remove Span

    inline fun <reified T>getSpan(spanId: String): T? where T : GenAIAgentSpan {
        return _spans[spanId] as? T
    }

    inline fun <reified T>getSpanOrThrow(id: String): T where T : GenAIAgentSpan {
        val span = _spans[id] ?: error("Span with id: $id not found")
        return span as? T
            ?: error("Span with id <$id> is not of expected type. Expected: <${T::class.simpleName}>, actual: <${span::class.simpleName}>")
    }

    private fun addSpan(span: GenAIAgentSpan) {
        val spanId = span.spanId
        val existingSpan = _spans[spanId]

        check(existingSpan == null) { "Span with id '$spanId' already added" }

        _spans[span.spanId] = span
    }

    private fun removeSpan(spanId: String): GenAIAgentSpan? {
        val removedSpan = _spans.remove(spanId)
        if (removedSpan == null) {
            logger.warn { "Span with id '$spanId' not found. Make sure you do not delete span with same id several times" }
        }

        return removedSpan
    }

    //endregion Add/Remove Span


    //region Private Methods

    private fun SpanBuilder.setAttribute(attribute: Attribute) {
        logger.debug { "Set gen_ai span attribute '${attribute.key}' with value '${attribute.value}' in a Span Builder" }

        when (attribute.value) {
            is String  -> setAttribute(attribute.key, attribute.value as String)
            is Boolean -> setAttribute(attribute.key, attribute.value as Boolean)
            is Long    -> setAttribute(attribute.key, attribute.value as Long)
            is Double  -> setAttribute(attribute.key, attribute.value as Double)
            is List<*> -> {
                when (attribute.value) {
                    String  -> setAttribute(AttributeKey.stringArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString() })
                    Boolean -> setAttribute(AttributeKey.booleanArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toBoolean() })
                    Long    -> setAttribute(AttributeKey.longArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toLong() })
                    Double  -> setAttribute(AttributeKey.doubleArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toDouble() })
                }
            }
            else -> throw IllegalStateException("Attribute '${attribute.key}' has unsupported type for value: ${attribute.value::class.simpleName}")
        }
    }

    private fun Span.setAttribute(attribute: Attribute) {
        logger.debug { "Set gen_ai span attribute '${attribute.key}' with value '${attribute.value}' in a Span" }

        when (attribute.value) {
            is String  -> setAttribute(attribute.key, attribute.value as String)
            is Boolean -> setAttribute(attribute.key, attribute.value as Boolean)
            is Long    -> setAttribute(attribute.key, attribute.value as Long)
            is Double  -> setAttribute(attribute.key, attribute.value as Double)
            is List<*> -> {
                when (attribute.value) {
                    String  -> setAttribute(AttributeKey.stringArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString() })
                    Boolean -> setAttribute(AttributeKey.booleanArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toBoolean() })
                    Long    -> setAttribute(AttributeKey.longArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toLong() })
                    Double  -> setAttribute(AttributeKey.doubleArrayKey(attribute.key), (attribute.value as List<*>).map { it.toString().toDouble() })
                }
            }
            else -> throw IllegalStateException("Attribute '${attribute.key}' has unsupported type for value: ${attribute.value::class.simpleName}")
        }
    }

    //endregion Private Methods
}