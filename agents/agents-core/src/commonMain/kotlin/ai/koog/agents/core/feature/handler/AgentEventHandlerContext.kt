package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * Provides the context for handling events specific to AI agents.
 * This interface extends the foundational event handling context, `EventHandlerContext`,
 * and is specialized for scenarios involving agents and their associated workflows or features.
 *
 * The `AgentEventHandlerContext` enables implementation of event-driven systems within
 * the AI Agent framework by offering hooks for custom event handling logic tailored to agent operations.
 */
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
    public val agent: AIAgent<*, *>,
    public val sessionId: String,
    public val strategy: AIAgentStrategy<*, *>,
    public val feature: TFeature,
) : AgentEventHandlerContext {
    /**
     * Reads the current AI agent strategy and executes the provided block of logic with it as a parameter.
     *
     * @param block A suspendable block of code that receives the current [AIAgentStrategy] as its parameter.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy<*, *>) -> Unit) {
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
    public val result: Any?
) : AgentEventHandlerContext

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 *
 * @property agentId The unique identifier of the agent associated with the error.
 * @property sessionId The identifier for the session during which the error occurred.
 * @property strategyName The name of the strategy being executed when the error occurred.
 * @property throwable The exception or error thrown during the execution.
 */
public data class AgentRunErrorHandlerContext(
    val agentId: String,
    val sessionId: String,
    val strategyName: String,
    val throwable: Throwable
) : AgentEventHandlerContext

/**
 * Represents the context passed to the handler that is executed before an agent is closed.
 *
 * @property agentId Identifier of the agent that is about to be closed.
 */
public data class AgentBeforeCloseHandlerContext(
    val agentId: String,
)
