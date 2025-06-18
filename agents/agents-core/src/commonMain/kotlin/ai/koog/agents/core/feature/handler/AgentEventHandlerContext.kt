package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStrategy

public interface AgentEventHandlerContext : EventHandlerContext

/**
 * Represents the context available during the start of an AI agent.
 *
 * @param TFeature The type of the feature object associated with this context.
 * @property strategy The AI agent strategy that defines the workflow and execution logic.
 * @property agent The AI agent associated with this context.
 * @property feature The feature-specific data associated with this context.
 */
public data class AgentStartHandlerContext<TFeature>(
    public val strategy: AIAgentStrategy,
    public val agent: AIAgent,
    public val feature: TFeature
) : AgentEventHandlerContext {
    /**
     * Reads the current AI agent strategy and executes the provided block of logic with it as a parameter.
     *
     * @param block A suspendable block of code that receives the current [AIAgentStrategy] as its parameter.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy) -> Unit) {
        block(strategy)
    }
}

public data class AgentFinishedHandlerContext(
    public val strategyName: String,
    public val result: String?
) : AgentEventHandlerContext
