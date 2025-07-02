package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttribute
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
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









    fun startSpan(
        span: GenAIAgentSpan,
        instant: Instant? = null,
    ): Span {

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
        return startedSpan
    }

    fun endSpan(
        span: GenAIAgentSpan,
        attributes: List<GenAIAttribute> = emptyList(),
        status: StatusCode = StatusCode.OK,
    ) {
        logger.debug { "Finishing the span (name: $${span.name}, id: ${span.spanId})" }

        val spanToFinish = span.span

        attributes.forEach { attribute -> spanToFinish.setAttribute(attribute) }

        spanToFinish.setStatus(status)
        spanToFinish.end()

        removeSpan(span.spanId)
    }












    fun endUnfinishedSpans(filter: (spanId: String) -> Boolean = { true }) {
        _spans.entries
            .filter { (id, _) ->
                val isRequireFinish = filter(id)
                isRequireFinish
            }
            .forEach { (id, span) ->
                logger.warn { "Force close span with id: $id" }
                endSpan(span = span, attributes = emptyList(), status = StatusCode.UNSET)
            }
    }

    fun endUnfinishedAgentRunSpans(agentId: String, runId: String) {
        val agentRunSpanId = InvokeAgentSpan.createId(agentId, runId)
        val agentSpanId = CreateAgentSpan.createId(agentId)

        endUnfinishedSpans(filter = { id -> id != agentSpanId && id != agentRunSpanId })
    }



    //region Add/Remove Span

    fun getSpan(spanId: String): GenAIAgentSpan? {
        return _spans[spanId]
    }

    fun getOrAddSpan(spanId: String, addBlock: () -> GenAIAgentSpan): GenAIAgentSpan {
        return _spans.getOrPut(spanId, addBlock)
    }

    private fun addSpan(span: GenAIAgentSpan) {
        val spanId = span.spanId
        val existingSpan = getSpan(spanId)

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