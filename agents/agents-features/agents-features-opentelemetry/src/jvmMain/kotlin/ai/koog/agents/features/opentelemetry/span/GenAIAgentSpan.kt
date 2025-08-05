package ai.koog.agents.features.opentelemetry.span

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.context.Context

internal abstract class GenAIAgentSpan(
    val parent: GenAIAgentSpan?,
) {

    companion object {
        private val logger = KotlinLogging.logger { }
    }

    private var _context: Context? = null

    private var _span: Span? = null

    var context: Context
        get() = _context ?: error("Context for span '$spanId' is not initialized")
        set(value) {
            _context = value
        }

    var span: Span
        get() = _span ?: error("Span '$spanId' is not started")
        set(value) {
            _span = value
        }

    val name: String
        get() = spanId.removePrefix(parent?.spanId ?: "").trimStart('.')

    open val kind: SpanKind = SpanKind.CLIENT

    abstract val spanId: String

    private val _attributes = mutableListOf<Attribute>()

    val attributes: List<Attribute>
        get() = _attributes

    private val _events = mutableListOf<GenAIAgentEvent>()

    val events: List<GenAIAgentEvent>
        get() = _events

    fun addAttribute(attribute: Attribute) {
        _attributes.add(attribute)
    }

    fun addEvent(event: GenAIAgentEvent) {
        _events.add(event)
    }

    @InternalAgentsApi
    fun clearEvents() {
        _events.clear()
    }
}
