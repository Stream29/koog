package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentContextBase
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
 * @param TFeature The type of feature associated with the strategy update.
 * @property runId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property feature The feature bound to the strategy update, providing additional contextual information.
 */
public class StrategyStartContext<TFeature, TStrategy: AIAgentStrategy<*, *, *>>(
    public val runId: String,
    public val strategy: TStrategy,
    public val agentContext: AIAgentContextBase<*>,
    public val feature: TFeature
) : StrategyEventHandlerContext

/**
 * Represents the context associated with the completion of an AI agent strategy execution.
 *
 * @param TFeature The type of feature associated with the strategy update.
 * @property runId A unique identifier for the session during which the strategy is being updated.
 * @property strategy The strategy being updated, encapsulating the AI agent's workflow logic.
 * @property feature The feature bound to the strategy update, providing additional contextual information.
 */
public class StrategyFinishContext<TFeature>(
    public val runId: String,
    public val strategy: AIAgentStrategy<*, *, *>,
    public val feature: TFeature,
    public val result: Any?
) : StrategyEventHandlerContext
