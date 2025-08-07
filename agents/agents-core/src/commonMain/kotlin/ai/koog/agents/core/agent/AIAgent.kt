@file:OptIn(InternalAgentsApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.executor.model.PromptExecutor

@OptIn(InternalAgentToolsApi::class)
internal class DirectToolCallsEnablerImpl : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
internal class AllowDirectToolCallsContext(val toolEnabler: DirectToolCallsEnabler)

@OptIn(InternalAgentToolsApi::class)
internal suspend inline fun <T> allowToolCalls(block: suspend AllowDirectToolCallsContext.() -> T) =
    AllowDirectToolCallsContext(DirectToolCallsEnablerImpl()).block()


/**
 * Represents an implementation of an AI agent that provides functionalities to execute prompts,
 * manage tools, handle agent pipelines, and interact with various configurable strategies and features.
 *
 * The agent operates within a coroutine scope and leverages a tool registry and feature context
 * to enable dynamic additions or configurations during its lifecycle. Its behavior is driven
 * by a local agent strategy and executed via a prompt executor.
 *
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy Strategy defining the local behavior of the agent.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @property clock The clock used to calculate message timestamps
 * @constructor Initializes the AI agent instance and prepares the feature context and pipeline for use.
 */
//@OptIn(ExperimentalUuidApi::class)
//private open class AIAgent<Input, Output, TStrategy : AIAgentStrategy<Input, Output>>(
//    public val promptExecutor: PromptExecutor,
//    private val strategy: TStrategy,
//    public val agentConfig: AIAgentConfigBase,
//    override val id: String = Uuid.random().toString(),
//    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
//    public val clock: Clock = Clock.System,
//    private val installFeatures: FeatureContext<TStrategy>.() -> Unit = {},
//) : AIAgentBase<Input, Output>, AIAgentEnvironment, Closeable {
//
//    private companion object {
//        private val logger = KotlinLogging.logger {}
//    }
//
//    /**
//     * The context for adding and configuring features in a Kotlin AI Agent instance.
//     *
//     * Note: The method is used to hide internal install() method from a public API to prevent
//     *       calls in an [AIAgent] instance, like `agent.install(MyFeature) { ... }`.
//     *       This makes the API a bit stricter and clear.
//     */
//    public class FeatureContext<TStrategy : AIAgentStrategy<*, *>> internal constructor(private val agent: AIAgent<*, *, TStrategy>) {
//        /**
//         * Installs and configures a feature into the current AI agent context.
//         *
//         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
//         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
//         */
//        public fun <Config : FeatureConfig, Feature : Any> install(
//            feature: AIAgentFeature<Config, Feature, in TStrategy>,
//            configure: Config.() -> Unit = {}
//        ) {
//            agent.install(feature, configure)
//        }
//    }
//
//    private var isRunning = false
//
//    private val runningMutex = Mutex()
//
//    private val pipeline = AIAgentPipeline<TStrategy>()
//
//    init {
//        FeatureContext(this).installFeatures()
//    }
//
//    override suspend fun run(agentInput: Input): Output {
//        runningMutex.withLock {
//            if (isRunning) {
//                throw IllegalStateException("Agent is already running")
//            }
//
//            isRunning = true
//        }
//
//        pipeline.prepareFeatures()
//
//        val sessionUuid = Uuid.random()
//        val runId = sessionUuid.toString()
//
//        return withContext(
//            AgentRunInfoContextElement(
//                agentId = id,
//                runId = runId,
//                agentConfig = agentConfig,
//                strategyName = strategy.name
//            )
//        ) {
//            val stateManager = AIAgentStateManager()
//            val storage = AIAgentStorage()
//
//            // Environment (initially equal to the current agent), transformed by some features
//            //   (ex: testing feature transforms it into a MockEnvironment with mocked tools)
//            val preparedEnvironment =
//                pipeline.transformEnvironment(strategy = strategy, agent = this@AIAgent, baseEnvironment = this@AIAgent)
//
//            val agentContext = AIAgentContext(
//                environment = preparedEnvironment,
//                agentInput = agentInput,
//                config = agentConfig,
//                llm = AIAgentLLMContext(
//                    tools = toolRegistry.tools.map { it.descriptor },
//                    toolRegistry = toolRegistry,
//                    prompt = agentConfig.prompt,
//                    model = agentConfig.model,
//                    promptExecutor = PromptExecutorProxy(
//                        executor = promptExecutor,
//                        pipeline = pipeline,
//                        runId = runId
//                    ),
//                    environment = preparedEnvironment,
//                    config = agentConfig,
//                    clock = clock
//                ),
//                stateManager = stateManager,
//                storage = storage,
//                runId = runId,
//                strategyName = strategy.name,
//                pipeline = pipeline,
//                id = id,
//            )
//
//            logger.debug { formatLog(agentId = id, runId = runId, message = "Starting agent execution") }
//            pipeline.onBeforeAgentStarted(
//                runId = runId,
//                agent = this@AIAgent,
//                strategy = strategy,
//                context = agentContext
//            )
//
//            var strategyResult = strategy.execute(context = agentContext, input = agentInput)
//            while (strategyResult == null && agentContext.getAgentContextData() != null) {
//                pipeline.onBeforeStrategyStarted(strategy, agentContext)
//                strategyResult = strategy.execute(context = agentContext, input = agentInput)
//            }
//
//            logger.debug { formatLog(agentId = id, runId = runId, message = "Finished agent execution") }
//            pipeline.onAgentFinished(agentId = id, runId = runId, result = strategyResult)
//
//            runningMutex.withLock {
//                isRunning = false
//            }
//
//            return@withContext strategyResult ?: error("result is null")
//        }
//    }
//
//    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
//        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()
//
//        logger.info {
//            formatLog(
//                agentRunInfo.agentId,
//                agentRunInfo.runId,
//                "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]"
//            )
//        }
//
//        val message = AgentToolCallsToEnvironmentMessage(
//            runId = agentRunInfo.runId,
//            content = toolCalls.map { call ->
//                AgentToolCallToEnvironmentContent(
//                    agentId = id,
//                    runId = agentRunInfo.runId,
//                    toolCallId = call.id,
//                    toolName = call.tool,
//                    toolArgs = call.contentJson
//                )
//            }
//        )
//
//        val results = processToolCallMultiple(message).mapToToolResult()
//        logger.debug {
//            "Received results from tools call (" +
//                    "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
//                    "results: [${results.joinToString(", ") { it.result?.toStringDefault() ?: "null" }}])"
//        }
//
//        return results
//    }
//
//    override suspend fun reportProblem(exception: Throwable) {
//        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()
//
//        logger.error(exception) {
//            formatLog(agentRunInfo.agentId, agentRunInfo.runId, "Reporting problem: ${exception.message}")
//        }
//
//        processError(
//            agentId = agentRunInfo.agentId,
//            runId = agentRunInfo.runId,
//            error = AgentServiceError(
//                type = AgentServiceErrorType.UNEXPECTED_ERROR,
//                message = exception.message ?: "unknown error"
//            )
//        )
//    }
//
//    override suspend fun close() {
//        pipeline.onAgentBeforeClosed(agentId = id)
//        pipeline.closeFeaturesStreamProviders()
//    }
//
//    //region Private Methods
//
//    private fun <Config : FeatureConfig, Feature : Any> install(
//        feature: AIAgentFeature<Config, Feature, in TStrategy>,
//        configure: Config.() -> Unit
//    ) {
//        pipeline.install(feature, configure)
//    }
//
//    @OptIn(InternalAgentToolsApi::class)
//    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
//        allowToolCalls {
//            logger.debug { "Handling tool call sent by server..." }
//            val tool = toolRegistry.getTool(content.toolName)
//            // Tool Args
//            val toolArgs = try {
//                tool.decodeArgs(content.toolArgs)
//            } catch (e: Exception) {
//                logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
//                return toolResult(
//                    message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
//                    toolCallId = content.toolCallId,
//                    toolName = content.toolName,
//                    agentId = strategy.name,
//                    result = null
//                )
//            }
//
//            pipeline.onToolCall(
//                runId = content.runId,
//                toolCallId = content.toolCallId,
//                tool = tool,
//                toolArgs = toolArgs
//            )
//
//            // Tool Execution
//            val toolResult = try {
//                @Suppress("UNCHECKED_CAST")
//                (tool as Tool<ToolArgs, ToolResult>).execute(toolArgs, toolEnabler)
//            } catch (e: ToolException) {
//
//                pipeline.onToolValidationError(
//                    runId = content.runId,
//                    toolCallId = content.toolCallId,
//                    tool = tool,
//                    toolArgs = toolArgs,
//                    error = e.message
//                )
//
//                return toolResult(
//                    message = e.message,
//                    toolCallId = content.toolCallId,
//                    toolName = content.toolName,
//                    agentId = strategy.name,
//                    result = null
//                )
//            } catch (e: Exception) {
//
//                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }
//
//                pipeline.onToolCallFailure(
//                    runId = content.runId,
//                    toolCallId = content.toolCallId,
//                    tool = tool,
//                    toolArgs = toolArgs,
//                    throwable = e
//                )
//
//                return toolResult(
//                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
//                    toolCallId = content.toolCallId,
//                    toolName = content.toolName,
//                    agentId = strategy.name,
//                    result = null
//                )
//            }
//
//            // Tool Finished with Result
//            pipeline.onToolCallResult(
//                runId = content.runId,
//                toolCallId = content.toolCallId,
//                tool = tool,
//                toolArgs = toolArgs,
//                result = toolResult
//            )
//
//            logger.debug { "Completed execution of ${content.toolName} with result: $toolResult" }
//
//            return toolResult(
//                toolCallId = content.toolCallId,
//                toolName = content.toolName,
//                agentId = strategy.name,
//                message = toolResult.toStringDefault(),
//                result = toolResult
//            )
//        }
//
//    private suspend fun processToolCallMultiple(message: AgentToolCallsToEnvironmentMessage): EnvironmentToolResultMultipleToAgentMessage {
//        // call tools in parallel and return results
//        val results = supervisorScope {
//            message.content
//                .map { call -> async { processToolCall(call) } }
//                .awaitAll()
//        }
//
//        return EnvironmentToolResultMultipleToAgentMessage(
//            runId = message.runId,
//            content = results
//        )
//    }
//
//    private fun toolResult(
//        toolCallId: String?,
//        toolName: String,
//        agentId: String,
//        message: String,
//        result: ToolResult?
//    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
//        toolCallId = toolCallId,
//        toolName = toolName,
//        agentId = agentId,
//        message = message,
//        toolResult = result
//    )
//
//    private suspend fun processError(agentId: String, runId: String, error: AgentServiceError) {
//        try {
//            throw error.asException()
//        } catch (e: AgentEngineException) {
//            logger.error(e) { "Execution exception reported by server!" }
//            pipeline.onAgentRunError(agentId = agentId, runId = runId, throwable = e)
//        }
//    }
//
//    private fun formatLog(agentId: String, runId: String, message: String): String =
//        "[agent id: $agentId, run id: $runId] $message"
//
//    //endregion Private Methods
//}

/**
 * Convenience builder that creates an instance of an [AIAgent] with string input and output and the specified parameters.
 *
 * @param executor The [PromptExecutor] responsible for executing prompts.
 * @param strategy The [AIAgentStrategy] defining the agent's behavior. Default is a single-run strategy.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param toolRegistry The [ToolRegistry] containing tools available to the agent. Default is an empty registry.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 */
//@OptIn(ExperimentalUuidApi::class)
//public fun <TStrategy : AIAgentStrategy<String, String>> AIAgent(
//    executor: PromptExecutor,
//    llmModel: LLModel,
//    id: String = Uuid.random().toString(),
//    strategy: TStrategy,
//    systemPrompt: String = "",
//    temperature: Double = 1.0,
//    numberOfChoices: Int = 1,
//    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
//    maxIterations: Int = 50,
//    installFeatures: FeatureContext<TStrategy>.() -> Unit = {}
//): AIAgent<String, String, TStrategy> = AIAgent(
//    id = id,
//    promptExecutor = executor,
//    strategy = strategy,
//    agentConfig = AIAgentConfig(
//        prompt = prompt(
//            id = "chat",
//            params = LLMParams(
//                temperature = temperature,
//                numberOfChoices = numberOfChoices
//            )
//        ) {
//            system(systemPrompt)
//        },
//        model = llmModel,
//        maxAgentIterations = maxIterations,
//    ),
//    toolRegistry = toolRegistry,
//    installFeatures = installFeatures
//)

/**
 * Convenience builder that creates an instance of an [AIAgent] with string input and output, [singleRunStrategy], and the specified parameters.
 *
 * @param executor The [PromptExecutor] responsible for executing prompts.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param toolRegistry The [ToolRegistry] containing tools available to the agent. Default is an empty registry.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 */
//@OptIn(ExperimentalUuidApi::class)
//public fun AIAgent(
//    executor: PromptExecutor,
//    llmModel: LLModel,
//    id: String = Uuid.random().toString(),
//    systemPrompt: String = "",
//    temperature: Double = 1.0,
//    numberOfChoices: Int = 1,
//    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
//    maxIterations: Int = 50,
//    installFeatures: FeatureContext<AIAgentGraphStrategy<String, String>>.() -> Unit = {}
//): AIAgent<String, String, AIAgentGraphStrategy<String, String>> = AIAgent(
//    id = id,
//    promptExecutor = executor,
//    strategy = singleRunStrategy(),
//    agentConfig = AIAgentConfig(
//        prompt = prompt(
//            id = "chat",
//            params = LLMParams(
//                temperature = temperature,
//                numberOfChoices = numberOfChoices
//            )
//        ) {
//            system(systemPrompt)
//        },
//        model = llmModel,
//        maxAgentIterations = maxIterations,
//    ),
//    toolRegistry = toolRegistry,
//    installFeatures = installFeatures
//)