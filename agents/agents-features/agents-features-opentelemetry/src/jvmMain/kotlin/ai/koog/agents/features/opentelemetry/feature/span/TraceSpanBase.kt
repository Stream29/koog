package ai.koog.agents.features.opentelemetry.feature.span

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

internal abstract class TraceSpanBase(
    protected val tracer: Tracer,
    val parentSpan: TraceSpanBase?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    abstract val spanId: String

    private var _context: Context? = null

    val context: Context
        get() = _context ?: error("Context for span '${spanId}' is not initialized")

    private var _span: Span? = null

    val span: Span
        get() = _span ?: error("Span '${spanId}' is not started")

    protected fun startInternal(attributes: List<GenAIAttribute>): Span {

        val parentContext = parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(spanId)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setParent(parentContext)

        attributes.forEach { attribute -> spanBuilder.setGenAIAttribute(attribute) }

        return spanBuilder.startSpan().also {
            _span = it
            _context = it.storeInContext(parentContext)
        }
    }

    fun endInternal(attributes: List<GenAIAttribute>, status: StatusCode) {
        attributes.forEach { attribute -> span.setGenAIAttribute(attribute) }
        span.setStatus(status)
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
