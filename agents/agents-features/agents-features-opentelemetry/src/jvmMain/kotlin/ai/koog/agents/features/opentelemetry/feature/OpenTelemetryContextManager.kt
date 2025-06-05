package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.feature.model.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.LLMCallStartEvent
import ai.koog.agents.features.common.message.FeatureMessage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import java.util.concurrent.ConcurrentHashMap

/**
 * TODO: SD -- ...
 */
public class OpenTelemetryContextManager(private val tracer: Tracer) {

    // Store context for each thread to ensure proper context propagation
    private val threadContextMap = ConcurrentHashMap<Long, Context>()

    // Capture the initial context when the manager is created
    private val initialContext = Context.current()

    init {
        // Store the initial context in the registry if it's not already set
        // This helps in case the manager is created before the main span
        if (ContextRegistry.getRootContext() == Context.root()) {
            ContextRegistry.setRootContext(initialContext)
        }
    }

    /**
     * Creates a span for the given message and sets up the context.
     *     Parameters: message — The feature message to create a span for
     *     Return: A pair containing the created span and its scope
     */
    public fun createSpanForMessage(message: FeatureMessage): Pair<Span, Scope> {
        val threadId = Thread.currentThread().id

        // First try to get the global root context from the registry
        val globalContext = ContextRegistry.getRootContext()

        // Then try to get stored context for this thread, or use the global/initial context if none exists
        val parentContext = threadContextMap.getOrDefault(threadId, globalContext)

        // Create a span for this event
        val spanName = "koog.event.${message.javaClass.simpleName}"
        val spanBuilder = tracer.spanBuilder(spanName)
            .setParent(parentContext)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("koog.event.class.name", message.javaClass.simpleName)
            .setAttribute("koog.event.type", message.messageType.name)

        val span = spanBuilder.startSpan()

        // Make the span the current context and store the scope for later closing
        val scope = span.makeCurrent()

        // Store the updated context for this thread
        val currentContext = Context.current()
        threadContextMap[Thread.currentThread().id] = currentContext

        // For important events, update the global context to ensure future events use this context
        // This helps with context propagation across different threads
        if (message is AIAgentStartedEvent || message is LLMCallStartEvent) {
            ContextRegistry.setRootContext(currentContext)
        }

        return Pair(span, scope)
    }

    /**
     * Clean up thread context map to prevent memory leaks
     */
    public fun close() {
        threadContextMap.clear()
    }
}