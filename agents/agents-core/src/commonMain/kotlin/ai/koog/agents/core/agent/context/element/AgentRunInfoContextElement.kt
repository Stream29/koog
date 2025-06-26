package ai.koog.agents.core.agent.context.element

import kotlin.coroutines.CoroutineContext

/**
 * TODO: SD --
 */
public data class AgentRunInfoContextElement(
    val agentId: String,
    val sessionId: String,
    val strategyName: String
) : CoroutineContext.Element {

    /**
     * TODO: SD --
     */
    public companion object Key : CoroutineContext.Key<AgentRunInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}