package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.extension.setAttributes
import ai.koog.agents.features.opentelemetry.extension.setEvents
import ai.koog.agents.features.opentelemetry.extension.setSpanStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

internal class SpanProcessor(private val tracer: Tracer) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private val _spans = ConcurrentHashMap<String, GenAIAgentSpan>()

    private val spansLock = ReentrantReadWriteLock()

    val spansCount: Int
        get() = _spans.count()

    fun addEventsToSpan(spanId: String, events: List<GenAIAgentEvent>) {
        spansLock.read {
            val span = _spans[spanId] ?: error("Span with id '$spanId' not found")
            events.forEach { event -> span.addEvent(event) }
        }
    }

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

        spanBuilder.setAttributes(span.attributes)

        val startedSpan = spanBuilder.startSpan()

        // Store newly started span
        addSpan(span)

        // Update span context and span properties
        span.span = startedSpan
        span.context = startedSpan.storeInContext(parentContext)

        logger.debug { "Span has been started (name: ${span.name}, id: ${span.spanId})" }
    }

    fun endSpan(
        span: GenAIAgentSpan,
        attributes: List<Attribute> = emptyList(),
        spanEndStatus: SpanEndStatus? = null
    ) {
        logger.debug { "Finishing the span (id: ${span.spanId})" }

        val spanToFinish = span.span

        spanToFinish.setAttributes(attributes)
        spanToFinish.setEvents(span.events)
        spanToFinish.setSpanStatus(spanEndStatus)
        spanToFinish.end()

        val removedSpan = _spans.remove(span.spanId)
        if (removedSpan == null) {
            logger.warn {
                "Span with id '${span.spanId}' not found. Make sure you do not delete span with same id several times"
            }
        }
    }

    inline fun <reified T> getSpan(spanId: String): T? where T : GenAIAgentSpan {
        return _spans[spanId] as? T
    }

    inline fun <reified T> getSpanOrThrow(spanId: String): T where T : GenAIAgentSpan {
        val span = _spans[spanId] ?: error("Span with id: $spanId not found")
        return span as? T
            ?: error(
                "Span with id <$spanId> is not of expected type. Expected: <${T::class.simpleName}>, actual: <${span::class.simpleName}>"
            )
    }

    fun endUnfinishedSpans(filter: (spanId: String) -> Boolean = { true }) {
        _spans.values
            .filter { span ->
                val isRequireFinish = filter(span.spanId)
                isRequireFinish
            }
            .forEach { span ->
                logger.warn { "Force close span with id: ${span.spanId}" }
                endSpan(
                    span = span,
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

    //region Private Methods

    private fun addSpan(span: GenAIAgentSpan) {
        spansLock.write {
            val spanId = span.spanId
            val existingSpan = _spans[spanId]

            check(existingSpan == null) { "Span with id '$spanId' already added" }

            _spans[span.spanId] = span
        }
    }

    //endregion Private Methods
}
