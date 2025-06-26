package ai.koog.agents.core.agent.context.element

import kotlin.coroutines.CoroutineContext

/**
 * TODO: SD --
 */
public data class NodeInfoContextElement(val nodeName: String) : CoroutineContext.Element {

    /**
     * TODO: SD --
     */
    public companion object Key : CoroutineContext.Key<NodeInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}