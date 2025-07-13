package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.simple.SimpleAIAgentStrategy
import ai.koog.agents.core.agent.entity.graph.ToolSelectionStrategy

/**
 * Builds a simply defined AI agent strategy that processes user input and produces an output.
 *
 * You can write your own custom agent logic (basically, any Kotlin code) inside the [simpleStrategy] builder using
 * the provided SimpleAgen
 *
 * @property name The unique identifier for this agent.
 * @param init Lambda that defines stages and nodes of this agent
 */
public fun <Input, Output> simpleStrategy(
    name: String,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    init: suspend SimpleAIAgentStrategyContext.(Input) -> Output,
): SimpleAIAgentStrategy<Input, Output> {
    return object : SimpleAIAgentStrategy<Input, Output>(
        name = name,
        toolSelectionStrategy = toolSelectionStrategy
    ) {
        override suspend fun execute(
            context: AIAgentContextBase<*>,
            input: Input
        ): Output {
            val strategyContext = SimpleAIAgentStrategyContext(
                toolSelectionStrategy,
                context as AIAgentContextBase<SimpleAIAgentStrategy<*, *>>
            )
            return strategyContext.init(input)
        }
    }
}

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
    public val agentContext: AIAgentContextBase<SimpleAIAgentStrategy<*, *>>
) : AIAgentContextBase<SimpleAIAgentStrategy<*, *>> by agentContext