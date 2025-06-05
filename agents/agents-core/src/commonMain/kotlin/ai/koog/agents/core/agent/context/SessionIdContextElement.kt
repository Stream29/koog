package ai.koog.agents.core.agent.context

import kotlin.coroutines.CoroutineContext

/**
 * TODO: SD --
 */
public data class SessionIdContextElement(val sessionId: String) : CoroutineContext.Element {

    /**
     * TODO: SD --
     */
    public companion object Key : CoroutineContext.Key<SessionIdContextElement>

    override val key: CoroutineContext.Key<*> = Key
}