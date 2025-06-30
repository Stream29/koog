package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.entity.AIAgentStrategy

/**
 * Defines the context specifically for handling strategy-related events within the AI agent framework.
 * Extends the base event handler context to include functionality and behavior dedicated to managing
 * the lifecycle and operations of strategies associated with AI agents.
 */
public interface StrategyEventHandlerContext : EventHandlerContext

/**
 * Represents the context for updating AI agent strategies during execution.
 *
 * @param FeatureT The type of feature associated with the strategy update.
 * @property sessionId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property feature The feature bound to the strategy update, providing additional contextual information.
 */
public class StrategyStartContext<FeatureT>(
    public val sessionId: String,
    public val strategy: AIAgentStrategy<*, *>,
    public val feature: FeatureT
) : StrategyEventHandlerContext {
    /**
     * Provides read-only access to the current AI agent strategy within the execution context.
     *
     * @param block A suspending lambda function to process the current strategy. The strategy is
     *              provided as an instance of [AIAgentStrategy] and allows reading its configuration
     *              or properties without modifying the state.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy<*, *>) -> Unit) {
        block(strategy)
    }
}

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 *
 * @param FeatureT The type of feature associated with the strategy update.
 * @property sessionId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property feature The feature bound to the strategy update, providing additional contextual information.
 */
public class StrategyFinishContext<FeatureT>(
    public val sessionId: String,
    public val strategy: AIAgentStrategy<*, *>,
    public val feature: FeatureT,
    public val result: Any?
) : StrategyEventHandlerContext {
    /**
     * Provides read-only access to the current AI agent strategy within the execution context.
     *
     * @param block A suspending lambda function to process the current strategy. The strategy is
     *              provided as an instance of [AIAgentStrategy] and allows reading its configuration
     *              or properties without modifying the state.
     */
    public suspend fun readStrategy(block: suspend (AIAgentStrategy<*, *>) -> Unit) {
        block(strategy)
    }
}

