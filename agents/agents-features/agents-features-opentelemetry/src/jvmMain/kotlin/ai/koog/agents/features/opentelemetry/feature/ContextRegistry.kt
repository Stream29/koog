package ai.koog.agents.features.opentelemetry.feature


import io.opentelemetry.context.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * A global registry for OpenTelemetry context that allows sharing context across different threads. This helps ensure that all spans created in different threads can be linked to the same trace.
 */
public object ContextRegistry {

    /**
     * Store the root context that will be used as parent for all spans
     */
    private val rootContext = AtomicReference(Context.root())

    /**
     * Set the root context that will be used as parent for all spans
     */
    public fun setRootContext(context: Context) {
        rootContext.set(context)
    }

    /**
     * Get the current root context
     */
    public fun getRootContext(): Context {
        return rootContext.get()
    }

    /**
     * Reset the root context to the default (empty) context
     */
    public fun reset() {
        rootContext.set(Context.root())
    }
}