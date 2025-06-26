package ai.koog.agents.features.opentelemetry.feature.span

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

internal abstract class TraceSpanBase(
    protected val tracer: Tracer,
    protected val parentSpan: TraceSpanBase?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    abstract val spanEvent: SpanEvent

    abstract val context: Context

    private var _span: Span? = null

    val span: Span
        get() = _span ?: error("Span '${spanEvent.id}' is not started")

    protected fun start(attributes: List<GenAIAttribute>): Span {
        val spanBuilder = tracer.spanBuilder(spanEvent.id)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setParent(parentSpan?.context ?: Context.current())

        attributes.forEach { attribute -> spanBuilder.setGenAIAttribute(attribute) }

        return spanBuilder.startSpan().also { _span = it }
    }

    protected fun end(attributes: List<GenAIAttribute>) {
        attributes.forEach { attribute -> span.setGenAIAttribute(attribute) }
        span.end()
    }

    //region Private Methods

    private fun SpanBuilder.setGenAIAttribute(attribute: GenAIAttribute) {
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

    private fun Span.setGenAIAttribute(attribute: GenAIAttribute) {
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
