package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

internal abstract class TraceSpanBase(
    protected val tracer: Tracer,
    protected val spanId: String,
    protected val parentContext: Context
) {

    abstract val span: Span

    abstract val attributes: Array<GenAIAttribute>

    abstract fun start()

    abstract fun end()

    fun create(): Span {
        val spanBuilder = tracer.spanBuilder(spanId)
            .setStartTimestamp(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setParent(parentContext)

        attributes.forEach { attribute ->
            when (attribute.value) {
                is String -> spanBuilder.setAttribute(attribute.key, attribute.value as String)
                is Boolean -> spanBuilder.setAttribute(attribute.key, attribute.value as Boolean)
                is Long -> spanBuilder.setAttribute(attribute.key, attribute.value as Long)
                is Double -> spanBuilder.setAttribute(attribute.key, attribute.value as Double)
                else -> spanBuilder.setAttribute(attribute.key, attribute.value.toString())
            }
        }
    }
}
