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
    public val agent: AIAgent,
    public val sessionId: String,
    public val strategy: AIAgentStrategy,
    public val feature: TFeature,
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

/**
 * Represents the context for handling the completion of an agent's execution.
 *
 * @property agentId The unique identifier of the agent that completed its execution.
 * @property sessionId The identifier of the session in which the agent was executed.
 * @property strategyName The name of the strategy executed by the agent.
 * @property result The optional result of the agent's execution, if available.
 */
public data class AgentFinishedHandlerContext(
    public val agentId: String,
    public val sessionId: String,
    public val strategyName: String,
    public val result: String?
) : AgentEventHandlerContext

/**
 * Provides contextual information to handle errors that occur during the execution of an agent run.
 *
 * @property strategyName The name of the strategy being executed when the error occurred.
 * @property throwable The exception or error that was thrown during the execution.
 */
public data class AgentRunErrorHandlerContext(
    val agentId: String,
    val sessionId: String,
    val strategyName: String,
    val throwable: Throwable
) : AgentEventHandlerContext
