package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.AIAgentMaxNumberOfIterationsReachedException
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.simple.SimpleAIAgentStrategy
import ai.koog.agents.core.agent.entity.graph.ToolSelectionStrategy
import ai.koog.agents.core.agent.entity.simple.SimpleAIAgentStrategyContext
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Builds a simply defined AI agent strategy that processes user input and produces an output.
 *
 * You can write your own custom agent logic (basically, any Kotlin code) inside the [simpleStrategy] builder using
 * the provided SimpleAgent
 *
 * @property name The unique identifier for this agent.
 * @param execute Lambda that defines stages and nodes of this agent
 */
public fun <Input, Output> simpleStrategy(
    name: String,
    toolSelectionStrategy: ToolSelectionStrategy = ToolSelectionStrategy.ALL,
    execute: suspend SimpleAIAgentStrategyContext.(Input) -> Output,
): SimpleAIAgentStrategy<Input, Output> {
    return object : SimpleAIAgentStrategy<Input, Output>(
        name = name
    ) {
        override suspend fun execute(
            context: SimpleAIAgentStrategyContext,
            input: Input
        ): Output? {
            val strategyContext = SimpleAIAgentStrategyContext(
                toolSelectionStrategy,
                context
            )
            return strategyContext.execute(input)
        }
    }
}

internal class IterationsChecker(
    val stateManager: AIAgentStateManager,
    val maxAgentIterations: Int,
    val logger: KLogger,
) {
    suspend fun validateMaxIterations() {
        stateManager.withStateLock { state ->
            if (++state.iterations > maxAgentIterations) {
                logger.error { "Max iterations limit ($maxAgentIterations) reached" }

                throw AIAgentMaxNumberOfIterationsReachedException(maxAgentIterations)
            }
        }
    }
}

internal class EnvironmentWrapper(
    val environment: AIAgentEnvironment,
    val iterationsChecker: IterationsChecker
) : AIAgentEnvironment {
    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        iterationsChecker.validateMaxIterations()

        return environment.executeTools(toolCalls)
    }

    override suspend fun reportProblem(exception: Throwable) {
        return environment.reportProblem(exception)
    }
}

internal class LLMWrapper(
    val llm: AIAgentLLMContext,
    val iterationsChecker: IterationsChecker
) : AIAgentLLMContext(
    llm.tools,
    llm.toolRegistry,
    llm.prompt,
    llm.model,
    llm.promptExecutor,
    llm.environment,
    llm.config,
    llm.clock
) {
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T {
        iterationsChecker.validateMaxIterations()

        return llm.writeSession(block)
    }


    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T {
        iterationsChecker.validateMaxIterations()

        return llm.readSession(block)
    }
}