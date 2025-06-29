package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Feature that collects comprehensive tracing data during agent execution and sends it to configured feature message processors.
 * 
 * Tracing is crucial for evaluation and analysis of the working agent, as it captures detailed information about:
 * - All LLM calls and their responses
 * - Prompts sent to LLMs
 * - Tool calls, arguments, and results
 * - Graph node visits and execution flow
 * - Agent lifecycle events (creation, start, finish, errors)
 * - Strategy execution events
 * 
 * This data can be used for debugging, performance analysis, auditing, and improving agent behavior.
 * 
 * Example of installing tracing to an agent:
 * ```kotlin
 * val agent = AIAgent(
 *     promptExecutor = executor,
 *     strategy = strategy,
 *     // other parameters...
 * ) {
 *     install(Tracing) {
 *         // Configure message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         addMessageProcessor(TraceFeatureMessageFileWriter(outputFile, fileSystem::sink))
 *         
 *         // Optionally filter messages
 *         messageFilter = { message -> 
 *             // Only trace LLM calls and tool calls
 *             message is BeforeLLMCallEvent || message is ToolCallEvent 
 *         }
 *     }
 * }
 * ```
 * 
 * Example of logs produced by tracing:
 * ```
 * AIAgentStartedEvent (agentId: agent-123, sessionId: session-456, strategyName: my-agent-strategy)
 * AIAgentStrategyStartEvent (sessionId: session-456, strategyName: my-agent-strategy)
 * AIAgentNodeExecutionStartEvent (sessionId: session-456, nodeName: definePrompt, input: user query)
 * AIAgentNodeExecutionEndEvent (sessionId: session-456, nodeName: definePrompt, input: user query, output: processed query)
 * BeforeLLMCallEvent (sessionId: session-456, prompt: Please analyze the following code...)
 * AfterLLMCallEvent (sessionId: session-456, response: I've analyzed the code and found...)
 * ToolCallEvent (sessionId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"})
 * ToolCallResultEvent (sessionId: session-456, toolName: readFile, toolArgs: {"path": "src/main.py"}, result: "def main():...")
 * AIAgentStrategyFinishedEvent (sessionId: session-456, strategyName: my-agent-strategy, result: Success)
 * AIAgentFinishedEvent (agentId: agent-123, sessionId: session-456, result: Success)
 * ```
 */
public class Tracing {

    /**
     * Feature implementation for the Tracing functionality.
     * 
     * This companion object implements [AIAgentFeature] and provides methods for creating
     * an initial configuration and installing the tracing feature in an agent pipeline.
     * 
     * To use tracing in your agent, install it during agent creation:
     * 
     * ```kotlin
     * val agent = AIAgent(...) {
     *     install(Tracing) {
     *         // Configure tracing here
     *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
     *     }
     * }
     * ```
     */
    public companion object Feature : AIAgentFeature<TraceFeatureConfig, Tracing> {

        private val logger = KotlinLogging.logger {  }

        override val key: AIAgentStorageKey<Tracing> =
            AIAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig(): TraceFeatureConfig = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warn { "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target." }
            }

            val interceptContext = InterceptContext(this, Tracing())

            //region Intercept Agent Events

            pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{ eventContext ->
                val event = AIAgentStartedEvent(
                    agentId = eventContext.agent.id,
                    sessionId = eventContext.sessionId,
                    strategyName = eventContext.strategy.name,
                )

                @Suppress("unused")
                eventContext.readStrategy { strategy ->
                    processMessage(config, event)
                }
            }

            pipeline.interceptAgentFinished(interceptContext) intercept@{ eventContext ->
                val event = AIAgentFinishedEvent(
                    agentId = eventContext.agentId,
                    sessionId = eventContext.sessionId,
                    result = eventContext.result?.toString(),
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentRunError(interceptContext) intercept@{ eventContext ->
                val event = AIAgentRunErrorEvent(
                    agentId = eventContext.agentId,
                    sessionId = eventContext.sessionId,
                    error = eventContext.throwable.toAgentError(),
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) intercept@{ eventContext ->
                val event = AIAgentBeforeCloseEvent(
                    agentId = eventContext.agentId,
                )
                processMessage(config, event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(interceptContext) intercept@{ eventContext ->
                val event = AIAgentStrategyStartEvent(
                    sessionId = eventContext.sessionId,
                    strategyName = eventContext.strategy.name,
                )

                eventContext.readStrategy { _ ->
                    processMessage(config, event)
                }
            }

            pipeline.interceptStrategyFinished(interceptContext) intercept@{ eventContext ->
                val event = AIAgentStrategyFinishedEvent(
                    sessionId = eventContext.sessionId,
                    strategyName = eventContext.strategy.name,
                    result = eventContext.result?.toString(),
                )
                processMessage(config, event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(interceptContext) intercept@{ eventContext ->
                val event = AIAgentNodeExecutionStartEvent(
                    sessionId = eventContext.context.sessionId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: ""
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterNode(interceptContext) intercept@{ eventContext ->
                val event = AIAgentNodeExecutionEndEvent(
                    sessionId = eventContext.context.sessionId,
                    nodeName = eventContext.node.name,
                    input = eventContext.input?.toString() ?: "",
                    output = eventContext.output?.toString() ?: ""
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ eventContext ->
                val event = BeforeLLMCallEvent(
                    sessionId = eventContext.sessionId,
                    prompt = eventContext.prompt,
                    model = eventContext.model,
                    tools = eventContext.tools.map { it.name }
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterLLMCall(interceptContext) intercept@{ eventContext ->
                val event = AfterLLMCallEvent(
                    sessionId = eventContext.sessionId,
                    prompt = eventContext.prompt,
                    model = eventContext.model,
                    responses = eventContext.responses
                )
                processMessage(config, event)
            }

            pipeline.interceptStartLLMStreaming(interceptContext) intercept@{ eventContext ->
                val event = StartLLMStreamingEvent(
                    sessionId = eventContext.sessionId,
                    prompt = eventContext.prompt,
                    model = eventContext.model,
                )
                processMessage(config, event)
            }

            pipeline.interceptBeforeExecuteMultipleChoices(interceptContext) intercept@{ eventContext ->
                val event = BeforeExecuteMultipleChoicesEvent(
                    sessionId = eventContext.sessionId,
                    prompt = eventContext.prompt,
                    model = eventContext.model,
                    tools = eventContext.tools.map { it.name }
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterExecuteMultipleChoices(interceptContext) intercept@{ eventContext ->
                val event = AfterExecuteMultipleChoicesEvent(
                    sessionId = eventContext.sessionId,
                    prompt = eventContext.prompt,
                    model = eventContext.model,
                    tools = eventContext.tools.map { it.name },
                    responses = eventContext.responses
                )
                processMessage(config, event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(interceptContext) intercept@{ eventContext ->
                val event = ToolCallEvent(
                    sessionId = eventContext.sessionId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.toolArgs
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationError(interceptContext) intercept@{ eventContext ->
                val event = ToolValidationErrorEvent(
                    sessionId = eventContext.sessionId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.toolArgs,
                    error = eventContext.error
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailure(interceptContext) intercept@{ eventContext ->
                val event = ToolCallFailureEvent(
                    sessionId = eventContext.sessionId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.toolArgs,
                    error = eventContext.throwable.toAgentError()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallResult(interceptContext) intercept@{ eventContext ->
                val event = ToolCallResultEvent(
                    sessionId = eventContext.sessionId,
                    toolName = eventContext.tool.name,
                    toolArgs = eventContext.toolArgs,
                    result = eventContext.result
                )
                processMessage(config, event)
            }

            //endregion Intercept Tool Call Events
        }

        //region Private Methods

        private suspend fun processMessage(config: TraceFeatureConfig, message: FeatureMessage) {
            if (!config.messageFilter(message)) {
                return
            }

            config.messageProcessor.onMessageForEachSafe(message)
        }

        //endregion Private Methods
    }
}
