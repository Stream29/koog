package ai.koog.agents.features.opentelemetry.feature.span

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.data.EventData
import io.opentelemetry.sdk.trace.data.SpanData
import java.util.concurrent.TimeUnit

internal abstract class TraceSpanBase(
    protected val tracer: Tracer,
    val parentSpan: TraceSpanBase?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _context: Context? = null

    private var _span: Span? = null

    abstract val spanId: String

    val context: Context
        get() = _context ?: error("Context for span '${spanId}' is not initialized")

    val span: Span
        get() = _span ?: error("Span '${spanId}' is not started")

    val spanName: String
        get() = spanId.removePrefix(parentSpan?.spanId ?: "").trimStart('.')

    protected fun startInternal(
        kind: SpanKind = SpanKind.CLIENT,
        attributes: List<GenAIAttribute> = emptyList(),
    ): Span {

        logger.debug { "$spanName. Span started (qualified id: $spanId)" }

        val parentContext = parentSpan?.context ?: Context.current()

        val spanBuilder = tracer.spanBuilder(spanName)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setSpanKind(kind)
            .setParent(parentContext)

        attributes.forEach { attribute -> spanBuilder.setGenAIAttribute(attribute) }

        return spanBuilder.startSpan().also {
            _span = it
            _context = it.storeInContext(parentContext)
        }
    }

    fun endInternal(attributes: List<GenAIAttribute>, status: StatusCode) {
        logger.debug { "$spanName. Span ended (qualified id: $spanId, status code: $status)" }

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
