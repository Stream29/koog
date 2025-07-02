package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

internal abstract class GenAIAgentSpan(
    val parent: GenAIAgentSpan?,
) {

    private var _context: Context? = null

    private var _span: Span? = null


    val context: Context
        get() = _context ?: error("Context for span '${spanId}' is not initialized")

    val span: Span
        get() = _span ?: error("Span '${spanId}' is not started")

    val name: String
        get() = spanId.removePrefix(parent?.spanId ?: "").trimStart('.')


    open val kind: SpanKind = SpanKind.CLIENT

    abstract val spanId: String

    abstract val attributes: List<Attribute>


    internal fun start(tracer: Tracer) {

    }

    internal fun end(tracer: Tracer) {

    }


    fun setContext(context: Context) {
        _context = context
    }

    fun setSpan (span: Span) {
        _span = span
    }
}
