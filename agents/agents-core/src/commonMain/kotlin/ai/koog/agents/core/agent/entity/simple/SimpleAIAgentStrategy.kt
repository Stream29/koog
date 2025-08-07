package ai.koog.agents.core.agent.entity.simple

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.graph.ToolSelectionStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.EnvironmentWrapper
import ai.koog.agents.core.dsl.builder.IterationsChecker
import ai.koog.agents.core.dsl.builder.LLMWrapper
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentPipeline
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Represents a strategy for managing and executing AI agent workflows built manually using []
 *
 * @property name The unique identifier for the strategy.
 */
@OptIn(InternalAgentsApi::class)
public abstract class SimpleAIAgentStrategy<Input, Output> internal constructor(
    override val name: String
) : AIAgentStrategy<Input, Output, SimpleAIAgentStrategyContext> {}



/**
 * Represents a context for configuring and executing AI agent strategies within a specific operational scope.
 *
 * This class acts as a bridge, integrating a strategy for selecting tools that an AI agent
 * uses during its operation and delegating contextual operations to an existing AI agent context.
 *
 * The `toolSelectionStrategy` provides the mechanism for determining the subset of tools
 * available for use within the defined context, while the `agentContext` serves as the
 * underlying execution context for the AI agent.
 *
 * Delegates functionality to an instance of `AIAgentContextBase`, inheriting its behaviors
 * and configurations for AI agent operations.
 *
 * @property toolSelectionStrategy A strategy defining which tools should be selected for the context.
 * @property agentContext The base context for the AI agent operations, delegated to by this class.
 */
public class SimpleAIAgentStrategyContext(
    private val toolSelectionStrategy: ToolSelectionStrategy,
    public val agentContext: AIAgentContextBase<AIAgentPipeline>
) : AIAgentContextBase<AIAgentPipeline> by agentContext {
    private val logger = KotlinLogging.logger {}

    private val iterationsChecker = IterationsChecker(
        agentContext.stateManager,
        agentContext.config.maxAgentIterations,
        logger
    )

    /**
     * The `environment` property represents the operational environment in which the AI agent interacts.
     * It wraps the base environment from the `agentContext` and adds functionality to monitor
     * and enforce constraints such as iteration limits through an `IterationsChecker`.
     *
     * The `EnvironmentWrapper` ensures that all tool execution and problem reporting operations
     * are delegated to the base environment, while also validating constraints before any actions are executed.
     *
     * This property serves as the primary interface for the AI agent to interact with its external environment
     * while adhering to defined operational rules and limits.
     */
    override val environment: AIAgentEnvironment = EnvironmentWrapper(agentContext.environment, iterationsChecker)

    /**
     * Represents the language model (LLM) context associated with the current AI agent strategy.
     *
     * This property provides an instance of [AIAgentLLMContext], wrapped with additional functionality
     * via [LLMWrapper], which enforces constraints and validations (e.g., iteration checks) during
     * read and write sessions. It serves as the primary interface for managing tools, prompts, models,
     * and other LLM-related operations within the current strategy context.
     *
     * The LLM context is responsible for interaction with the AI agent's execution and environment layers,
     * concurrent operation handling, and context-specific configurations defined in the containing class.
     */
    override val llm: AIAgentLLMContext = LLMWrapper(agentContext.llm, iterationsChecker)
}