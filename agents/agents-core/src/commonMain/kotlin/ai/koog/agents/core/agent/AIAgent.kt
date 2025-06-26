package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.context.element.getAgentRunInfoElement
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.TerminationTool
import ai.koog.agents.core.exception.AgentEngineException
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.model.AgentServiceError
import ai.koog.agents.core.model.AgentServiceErrorType
import ai.koog.agents.core.model.message.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.utils.Closeable
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.text.TextContentBuilder
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(InternalAgentToolsApi::class)
private class DirectToolCallsEnablerImpl : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
private class AllowDirectToolCallsContext(val toolEnabler: DirectToolCallsEnabler)

@OptIn(InternalAgentToolsApi::class)
private suspend inline fun <T> allowToolCalls(block: suspend AllowDirectToolCallsContext.() -> T) =
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
@OptIn(ExperimentalUuidApi::class)
public open class AIAgent(
    public val promptExecutor: PromptExecutor,
    private val strategy: AIAgentStrategy,
    public val agentConfig: AIAgentConfigBase,
    override val id: String = Uuid.random().toString(),
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    public val clock: Clock = Clock.System,
    private val installFeatures: FeatureContext.() -> Unit = {},
) : AIAgentBase, AIAgentEnvironment, Closeable {

    private companion object {
        private val logger = KotlinLogging.logger {}
        private const val INVALID_TOOL = "Can not call tools beside \"${TerminationTool.NAME}\"!"
        private const val NO_CONTENT = "Could not find \"content\", but \"error\" is also absent!"
        private const val NO_RESULT = "Required tool argument value not found: \"${TerminationTool.ARG}\"!"
    }

    /**
     * Creates an instance of [AIAgent] with the specified parameters.
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
    public constructor(
        executor: PromptExecutor,
        llmModel: LLModel,
        strategy: AIAgentStrategy = singleRunStrategy(),
        systemPrompt: String = "",
        temperature: Double = 1.0,
        numberOfChoices: Int = 1,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        maxIterations: Int = 50,
        installFeatures: FeatureContext.() -> Unit = {}
    ) : this(
        promptExecutor = executor,
        strategy = strategy,
        agentConfig = AIAgentConfig(
            prompt = prompt("chat", params = LLMParams(temperature = temperature, numberOfChoices = numberOfChoices)) {
                system(systemPrompt)
            },
            model = llmModel,
            maxAgentIterations = maxIterations,
        ),
        toolRegistry = toolRegistry,
        installFeatures = installFeatures
    )

    /**
     * The context for adding and configuring features in a Kotlin AI Agent instance.
     *
     * Note: The method is used to hide internal install() method from a public API to prevent
     *       calls in an [AIAgent] instance, like `agent.install(MyFeature) { ... }`.
     *       This makes the API a bit stricter and clear.
     */
    public class FeatureContext internal constructor(private val agent: AIAgent) {
        /**
         * Installs and configures a feature into the current AI agent context.
         *
         * @param feature the feature to be added, defined by an implementation of [AIAgentFeature], which provides specific functionality
         * @param configure an optional lambda to customize the configuration of the feature, where the provided [Config] can be modified
         */
        public fun <Config : FeatureConfig, Feature : Any> install(
            feature: AIAgentFeature<Config, Feature>,
            configure: Config.() -> Unit = {}
        ) {
            agent.install(feature, configure)
        }
    }

    private var isRunning = false

    private val runningMutex = Mutex()

    private val agentResultDeferred: CompletableDeferred<String?> = CompletableDeferred()

    private val pipeline = AIAgentPipeline()

    init {
        FeatureContext(this).installFeatures()
    }

    override suspend fun run(agentInput: String) {
        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
        }

        pipeline.prepareFeatures()

        val sessionUuid = Uuid.random()
        val runId = sessionUuid.toString()

        withContext(AgentRunInfoContextElement(agentId = id, sessionId = runId, strategyName = strategy.name)) {

            val stateManager = AIAgentStateManager()
            val storage = AIAgentStorage()

            // Environment (initially equal to the current agent), transformed by some features
            //   (ex: testing feature transforms it into a MockEnvironment with mocked tools)
            val preparedEnvironment = pipeline.transformEnvironment(strategy, this@AIAgent, this@AIAgent)

            val agentContext = AIAgentContext(
                sessionId = runId,
                environment = preparedEnvironment,
                agentInput = agentInput,
                config = agentConfig,
                llm = AIAgentLLMContext(
                    tools = toolRegistry.tools.map { it.descriptor },
                    prompt = agentConfig.prompt,
                    model = agentConfig.model,
                    promptExecutor = PromptExecutorProxy(
                        executor = promptExecutor,
                        pipeline = pipeline
                    ),
                    environment = preparedEnvironment,
                    config = agentConfig,
                    clock = clock,
                    toolRegistry = toolRegistry
                ),
                stateManager = stateManager,
                storage = storage,
                strategyName = strategy.name,
                pipeline = pipeline,
            )

            pipeline.onBeforeAgentStarted(sessionId = runId, agent = this@AIAgent, strategy = strategy)

            strategy.execute(context = agentContext, input = agentInput)
        }

        runningMutex.withLock {
            isRunning = false
            if (!agentResultDeferred.isCompleted) {
                agentResultDeferred.complete(null)
            }
        }
    }

    /**
     * Executes the AI agent using a builder to construct the textual input.
     *
     * This method allows for constructing a complex input by utilizing the functionalities
     * of [TextContentBuilder]. The builder is applied to create the structured input which
     * is then used to initiate the agent's execution.
     *
     * @param builder a lambda function applied to a [TextContentBuilder] instance to build the input text for the agent
     */
    public suspend fun run(builder: suspend TextContentBuilder.() -> Unit) {
        run(agentInput = TextContentBuilder().apply { this.builder() }.build())
    }

    override suspend fun runAndGetResult(agentInput: String): String? {
        run(agentInput)
        agentResultDeferred.await()
        return agentResultDeferred.getCompleted()
    }

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {

        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElement() ?: throw IllegalStateException("Agent run info not found")

        logger.info {
            formatLog(agentRunInfo.agentId, agentRunInfo.sessionId, "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]")
        }

        val message = AgentToolCallsToEnvironmentMessage(
            sessionId = agentRunInfo.sessionId,
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = strategy.name,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        logger.debug {
            "Received results from tools call (" +
                    "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                    "results: [${results.joinToString(", ") { it.result?.toStringDefault() ?: "null" }}])"
        }

        return results
    }

    override suspend fun reportProblem(exception: Throwable) {

        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElement() ?: throw IllegalStateException("Agent run info not found")

        logger.error(exception) {
            formatLog(agentRunInfo.agentId, agentRunInfo.sessionId, "Reporting problem: ${exception.message}")
        }

        processError(
            agentId = agentRunInfo.agentId,
            sessionId = agentRunInfo.sessionId,
            error = AgentServiceError(
                type = AgentServiceErrorType.UNEXPECTED_ERROR,
                message = exception.message ?: "unknown error"
            )
        )
    }

    override suspend fun sendTermination(result: String?) {

        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElement() ?: throw IllegalStateException("Agent run info not found")

        logger.info {
            formatLog(agentRunInfo.agentId, agentRunInfo.sessionId, "Sending final result")
        }

        val message = AgentTerminationToEnvironmentMessage(
            sessionId = agentRunInfo.sessionId,
            content = AgentToolCallToEnvironmentContent(
                agentId = strategy.name,
                toolCallId = null,
                toolName = TerminationTool.NAME,
                toolArgs = JsonObject(mapOf(TerminationTool.ARG to JsonPrimitive(result)))
            )
        )

        terminate(agentId = agentRunInfo.agentId, sessionId = agentRunInfo.sessionId, message = message)
    }

    override suspend fun close() {
        pipeline.closeFeaturesStreamProviders()
    }

    //region Private Methods

    private fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
        allowToolCalls {
            logger.debug { "Handling tool call sent by server..." }
            val tool = toolRegistry.getTool(content.toolName)
            // Tool Args
            val toolArgs = try {
                tool.decodeArgs(content.toolArgs)
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
                return toolResult(
                    message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            pipeline.onToolCall(tool = tool, toolArgs = toolArgs)

            // Tool Execution
            val toolResult = try {
                @Suppress("UNCHECKED_CAST")
                (tool as Tool<ToolArgs, ToolResult>).execute(toolArgs, toolEnabler)
            } catch (e: ToolException) {

                pipeline.onToolValidationError(tool = tool, toolArgs = toolArgs, error = e.message)

                return toolResult(
                    message = e.message,
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            } catch (e: Exception) {

                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }

                pipeline.onToolCallFailure(tool = tool, toolArgs = toolArgs, throwable = e)

                return toolResult(
                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = strategy.name,
                    result = null
                )
            }

            // Tool Finished with Result
            pipeline.onToolCallResult(tool = tool, toolArgs = toolArgs, result = toolResult)

            logger.debug { "Completed execution of ${content.toolName} with result: $toolResult" }

            return toolResult(
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = strategy.name,
                message = toolResult.toStringDefault(),
                result = toolResult
            )
        }

    private suspend fun processToolCallMultiple(message: AgentToolCallsToEnvironmentMessage): EnvironmentToolResultMultipleToAgentMessage {
        // call tools in parallel and return results
        val results = supervisorScope {
            message.content
                .map { call -> async { processToolCall(call) } }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            sessionId = message.sessionId,
            content = results
        )
    }

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    private suspend fun terminate(agentId: String, sessionId: String, message: AgentTerminationToEnvironmentMessage) {
        val messageContent = message.content
        val messageError = message.error

        logger.debug { "Finished execution chain, processing final result (content: $messageContent, error: $messageError)" }

        if (messageError == null) {

            check(messageContent != null) { NO_CONTENT }
            check(messageContent.toolName == TerminationTool.NAME) { INVALID_TOOL }

            val element = messageContent.toolArgs[TerminationTool.ARG]
            check(element != null) { NO_RESULT }

            val result = element.jsonPrimitive.contentOrNull

            logger.debug { "Final result sent by server: $result" }

            pipeline.onAgentFinished(agentId = agentId, sessionId = sessionId, strategyName = strategy.name, result = result)
            agentResultDeferred.complete(result)
        } else {
            processError(agentId = agentId, sessionId = sessionId, error = messageError)
        }
    }

    private suspend fun processError(agentId: String, sessionId: String, error: AgentServiceError) {
        try {
            throw error.asException()
        } catch (e: AgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }
            pipeline.onAgentRunError(agentId = agentId, sessionId = sessionId, strategyName = strategy.name, throwable = e)
        }
    }

    private fun formatLog(agentId: String, sessionId: String, message: String): String =
        "[agent id: $agentId, session id: $sessionId] $message"

    //endregion Private Methods
}

/**
 * Creates a single-run strategy for an AI agent.
 * This strategy defines a simple execution flow where the agent processes input,
 * calls tools, and sends results back to the agent.
 * The flow consists of the following steps:
 * 1. Start the agent.
 * 2. Call the LLM with the input.
 * 3. Execute a tool based on the LLM's response.
 * 4. Send the tool result back to the LLM.
 * 5. Repeat until LLM indicates no further tool calls are needed or the agent finishes.
 */
public fun singleRunStrategy(): AIAgentStrategy = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}