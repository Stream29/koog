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
 * Represents the context for creating and managing an AI agent within a specific strategy.
 *
 * @param FeatureT The type of the feature associated with the context.
 * @property strategy The AI agent strategy that defines the workflow and execution logic for the AI agent.
 * @property agent The AI agent being managed or operated upon in the context.
 * @property feature An additional feature or configuration associated with the context.
 */
public class AgentTransformEnvironmentContext<FeatureT>(
    public val strategy: AIAgentStrategy<*, *>,
    public val agent: AIAgent<*, *>,
    public val feature: FeatureT
) : AgentEventHandlerContext {
    /**
     * Executes a given block of code with the `AIAgentStrategy` instance of this context.
     *
     * @param block A suspending lambda function that receives the `AIAgentStrategy` instance.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy<*, *>) -> Unit) {
        block(strategy)
    }
}

/**
 * Represents the context available during the start of an AI agent.
 *
 * @param TFeature The type of the feature object associated with this context.
 * @property strategy The AI agent strategy that defines the workflow and execution logic.
 * @property agent The AI agent associated with this context.
 * @property feature The feature-specific data associated with this context.
 */
public data class AgentStartContext<TFeature>(
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
 * @property result The optional result of the agent's execution, if available.
 */
public data class AgentFinishedContext(
    public val agentId: String,
    public val sessionId: String,
    public val result: Any?
) : AgentEventHandlerContext

/**
 * Represents the context for handling errors that occur during the execution of an agent run.
 *
 * @property agentId The unique identifier of the agent associated with the error.
 * @property sessionId The identifier for the session during which the error occurred.
 * @property throwable The exception or error thrown during the execution.
 */
public data class AgentRunErrorContext(
    val agentId: String,
    val sessionId: String,
    val throwable: Throwable
) : AgentEventHandlerContext
