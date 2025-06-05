package ai.koog.agents.features.opentelemetry.feature.context

import kotlin.coroutines.CoroutineContext

/**
 * TODO: SD --
 */
public data class NodeNameContextElement(val nodeName: String) : CoroutineContext.Element {

    /**
     * TODO: SD --
     */
    public companion object Key : CoroutineContext.Key<NodeNameContextElement>

    override val key: CoroutineContext.Key<*> = Key
}