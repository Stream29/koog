package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.handler.*
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into stage contexts
 * - Intercept agent creation
 * - Intercept node execution before and after it happens
 * - Intercept LLM (Language Learning Model) calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 */
public class AIAgentPipeline {

    /**
     * Companion object for the AgentPipeline class.
     */
    private companion object {
        /**
         * Logger instance for the AgentPipeline class.
         */
        private val logger = KotlinLogging.logger {  }
    }

    private val featurePrepareDispatcher = Dispatchers.Default.limitedParallelism(5)

    /**
     * Map of registered features and their configurations.
     * Keys are feature storage keys, values are feature configurations.
     */
    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, FeatureConfig> = mutableMapOf()

    /**
     * Map of agent handlers registered for different features.
     * Keys are feature storage keys, values are agent handlers.
     */
    private val agentHandlers: MutableMap<AIAgentStorageKey<*>, AgentHandler<*>> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    private val strategyHandlers: MutableMap<AIAgentStorageKey<*>, StrategyHandler<*>> = mutableMapOf()

    /**
     * Map of stage context handlers registered for different features.
     * Keys are feature storage keys, values are stage context handlers.
     */
    private val agentContextHandler: MutableMap<AIAgentStorageKey<*>, AgentContextHandler<*>> = mutableMapOf()

    /**
     * Map of node execution handlers registered for different features.
     * Keys are feature storage keys, values are node execution handlers.
     */
    private val executeNodeHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteNodeHandler> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    private val executeToolHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteToolHandler> = mutableMapOf()

    /**
     * Map of LLM execution handlers registered for different features.
     * Keys are feature storage keys, values are LLM execution handlers.
     */
    private val executeLLMHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteLLMHandler> = mutableMapOf()

    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * This method initializes the feature with a custom configuration and registers it in the pipeline.
     * The feature's message processors are initialized during installation.
     *
     * @param Config The type of the feature configuration
     * @param Feature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }

    internal suspend fun prepareFeatures() {
        withContext(featurePrepareDispatcher) {
            registeredFeatures.values.forEach { featureConfig ->
                featureConfig.messageProcessor.map { processor ->
                    launch {
                        logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
                        processor.initialize()
                        logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
                    }
                }
            }
        }
    }

    /**
     * Closes all feature stream providers.
     *
     * This internal method properly shuts down all message processors of registered features,
     * ensuring resources are released appropriately.
     */
    internal suspend fun closeFeaturesStreamProviders() {
        registeredFeatures.values.forEach { config -> config.messageProcessor.forEach { provider -> provider.close() } }
    }

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param strategy The strategy being executed by the agent
     * @param agent The agent instance for which the execution has started
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onBeforeAgentStarted(sessionId: String, agent: AIAgent<*, *>, strategy: AIAgentStrategy<*, *>) {
        agentHandlers.values.forEach { handler ->
            val context = AgentStartHandlerContext(agent = agent, sessionId = sessionId, strategy = strategy, feature = handler.feature)
            handler.handleBeforeAgentStartedUnsafe(context)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param strategyName The name of the strategy that was executed
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun <TResult> onAgentFinished(
        agentId: String,
        sessionId: String,
        strategyName: String,
        result: TResult
    ) {
        val eventContext = AgentFinishedHandlerContext(agentId = agentId, sessionId = sessionId, strategyName = strategyName, result = result)
        agentHandlers.values.forEach { handler -> handler.agentFinishedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param strategyName The name of the strategy during which the error occurred
     * @param throwable The exception that was thrown during agent execution
     */
    public suspend fun onAgentRunError(
        agentId: String,
        sessionId: String,
        strategyName: String,
        throwable: Throwable
    ) {
        val eventContext = AgentRunErrorHandlerContext(agentId = agentId, sessionId = sessionId, strategyName = strategyName, throwable = throwable)
        agentHandlers.values.forEach { handler -> handler.agentRunErrorHandler.handle(eventContext) }
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public suspend fun onAgentBeforeClosed(
        agentId: String
    ) {
        val eventContext = AgentBeforeCloseHandlerContext(agentId = agentId)
        agentHandlers.values.forEach { handler -> handler.agentBeforeCloseHandler.handle(eventContext) }
    }

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param strategy The strategy associated with the agent
     * @param agent The agent instance for which the environment is being transformed
     * @param baseEnvironment The initial environment to be transformed
     * @return The transformed environment after all handlers have been applied
     */
    public fun transformEnvironment(
        strategy: AIAgentStrategy<*, *>,
        agent: AIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        return agentHandlers.values.fold(baseEnvironment) { env, handler ->
            val eventContext = AgentCreateContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.transformEnvironmentUnsafe(eventContext, env)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyStarted(strategy: AIAgentStrategy<*, *>, context: AIAgentContextBase) {
        strategyHandlers.values.forEach { handler ->
            val updateContext = StrategyUpdateContext(context.sessionId, strategy, handler.feature)
            handler.handleStrategyStartedUnsafe(updateContext)
        }
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun <TResult> onStrategyFinished(strategy: AIAgentStrategy<*, *>, context: AIAgentContextBase, result: TResult) {
        strategyHandlers.values.forEach { handler ->
            val updateContext = StrategyUpdateContext(sessionId = context.sessionId, strategy = strategy, feature = handler.feature)
            handler.handleStrategyFinishedUnsafe(updateContext, result)
        }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger Stage Context Handlers

    /**
     * Retrieves all features associated with the given stage context.
     *
     * This method collects features from all registered stage context handlers
     * that are applicable to the provided context.
     *
     * @param context The stage context for which to retrieve features
     * @return A map of feature keys to their corresponding feature instances
     */
    public fun getAgentFeatures(context: AIAgentContextBase): Map<AIAgentStorageKey<*>, Any> {
        return agentContextHandler.mapValues { (_, featureProvider) ->
            featureProvider.handle(context)
        }
    }

    //endregion Trigger Stage Context Handlers

    //region Trigger Node Handlers

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param node The node that is about to be executed
     * @param context The stage context in which the node is being executed
     * @param input The input data for the node execution
     */
    public suspend fun onBeforeNode(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) {
        val eventContext = BeforeNodeHandlerContext(context, node, input)
        executeNodeHandlers.values.forEach { handler -> handler.beforeNodeHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The stage context in which the node was executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node execution
     */
    public suspend fun onAfterNode(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        output: Any?
    ) {
        val eventContext = AfterNodeHandlerContext(context, node, input, output)
        executeNodeHandlers.values.forEach { handler -> handler.afterNodeHandler.handle(eventContext) }
    }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     */
    public suspend fun onBeforeLLMCall(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel) {
        val eventContext = BeforeLLMCallHandlerContext(prompt, tools, model)
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param responses A single or multiple response messages received from the language model
     */
    public suspend fun onAfterLLMCall(prompt: Prompt, tools: List<ToolDescriptor>, model: LLModel, responses: List<Message.Response>) {
        val eventContext = AfterLLMCallHandlerContext(prompt, tools, model, responses)
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallHandler.handle(eventContext) }
    }

    /**
     * Initiates the process of handling a streaming event for a large language model (LLM).
     *
     * @param prompt The input prompt provided to the LLM for generating responses.
     * @param model The large language model instance being used for the streaming process.
     */
    public suspend fun onStartLLMStreaming(prompt: Prompt, model: LLModel) {
        val eventContext = StartLLMStreamingHandlerContext(prompt, model)
        executeLLMHandlers.values.forEach { handler -> handler.startLLMStreamingHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param tool The tool that is being called
     * @param toolArgs The arguments provided to the tool
     */
    public suspend fun onToolCall(tool: Tool<*, *>, toolArgs: ToolArgs) {
        val eventContext = ToolCallHandlerContext(tool, toolArgs)
        executeToolHandlers.values.forEach { handler -> handler.toolCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public suspend fun onToolValidationError(tool: Tool<*, *>, toolArgs: ToolArgs, error: String) {
        val eventContext = ToolValidationErrorHandlerContext(tool, toolArgs, error)
        executeToolHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public suspend fun onToolCallFailure(tool: Tool<*, *>, toolArgs: ToolArgs, throwable: Throwable) {
        val eventContext = ToolCallFailureHandlerContext(tool, toolArgs, throwable)
        executeToolHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public suspend fun onToolCallResult(tool: Tool<*, *>, toolArgs: ToolArgs, result: ToolResult?) {
        val eventContext = ToolCallResultHandlerContext(tool, toolArgs, result)
        executeToolHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(eventContext) }
    }

    //endregion Trigger Tool Call Handlers

    //region Interceptors

    /**
     * Set feature handler for Context Stage events
     *
     * @param handler The handler responsible for processing the feature within the stage context
     *
     * Example:
     * ```
     * pipeline.interceptContextStageFeature(MyFeature) { stageContext ->
     *   // Inspect stage context
     * }
     * ```
     */
    public fun <TFeature : Any> interceptContextAgentFeature(
        feature: AIAgentFeature<*, TFeature>,
        handler: AgentContextHandler<TFeature>,
    ) {
        agentContextHandler[feature.key] = handler
    }

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
     * @param context The context of the feature being intercepted, providing access to the feature key and implementation
     * @param transform A function that transforms the environment, with access to the agent creation context
     *
     * Example:
     * ```
     * pipeline.interceptEnvironmentCreated(InterceptContext) { environment ->
     *     // Modify the environment based on agent context
     *     environment.copy(
     *         variables = environment.variables + mapOf("customVar" to "value")
     *     )
     * }
     * ```
     */
    public fun <TFeature : Any> interceptEnvironmentCreated(
        context: InterceptContext<TFeature>,
        transform: AgentCreateContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.environmentTransformer = AgentEnvironmentTransformer { context, env -> context.transform(env) }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeAgentStarted(InterceptContext) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeAgentStarted(
        context: InterceptContext<TFeature>,
        handle: suspend AgentStartHandlerContext<TFeature>.() -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.beforeAgentStartedHandler = BeforeAgentStartedHandler { context ->
            handle(context)
        }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentFinished(InterceptContext { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentFinished(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentFinishedHandlerContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentFinishedHandler = AgentFinishedHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentRunError(InterceptContext) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentRunError(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentRunErrorHandlerContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentRunErrorHandler = AgentRunErrorHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param TFeature The type of feature this handler is associated with.
     * @param context The context containing details about the feature and its implementation.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *                The function receives the feature instance and the event context as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptAgentBeforeClosed(InterceptContext) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentBeforeClosed(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentBeforeCloseHandlerContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentBeforeCloseHandler = AgentBeforeCloseHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarted(InterceptContext) {
     *     val strategyId = strategy.id
     *     logger.info("Strategy $strategyId has started execution")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyStarted(
        context: InterceptContext<TFeature>,
        handle: suspend StrategyUpdateContext<TFeature>.() -> Unit
    ) {
        val existingHandler = strategyHandlers.getOrPut(context.feature.key) { StrategyHandler(context.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${context.featureImpl::class}>, but get a handler of type <${context.feature.key}> instead. " +
                        "Skipping adding strategy started interceptor for feature."
            }
            return
        }

        existingHandler.strategyStartedHandler = StrategyStartedHandler { updateContext ->
            handle(updateContext)
        }
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param handle A suspend function that processes the completion of a strategy, accepting the strategy name
     *               and its result as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyFinished(InterceptContext) { result ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyFinished(
        context: InterceptContext<TFeature>,
        handle: suspend StrategyUpdateContext<TFeature>.(Any?) -> Unit
    ) {
        val existingHandler = strategyHandlers.getOrPut(context.feature.key) { StrategyHandler(context.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${context.featureImpl::class}>, but get a handler of type <${context.feature.key}> instead. " +
                        "Skipping adding strategy finished interceptor for feature."
            }
            return
        }

        existingHandler.strategyFinishedHandler = StrategyFinishedHandler { updateContext, result ->
            handle(updateContext, result)
        }
    }

    /**
     * Intercepts node execution before it starts.
     *
     * @param handle The handler that processes before-node events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeNode(InterceptContext) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} is about to execute with input: ${eventContext.input}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeNode(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: BeforeNodeHandlerContext) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(interceptContext.feature.key) { ExecuteNodeHandler() }

        existingHandler.beforeNodeHandler = BeforeNodeHandler { eventContext: BeforeNodeHandlerContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts node execution after it completes.
     *
     * @param handle The handler that processes after-node events
     *
     * Example:
     * ```
     * pipeline.interceptAfterNode(InterceptContext) { eventContext ->
     *     logger.info("Node ${eventContext.node.name} executed with input: ${eventContext.input} and produced output: ${eventContext.output}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterNode(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AfterNodeHandlerContext) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(interceptContext.feature.key) { ExecuteNodeHandler() }

        existingHandler.afterNodeHandler = AfterNodeHandler { eventContext: AfterNodeHandlerContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeLLMCall(InterceptContext) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: BeforeLLMCallHandlerContext) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(interceptContext.feature.key) { ExecuteLLMHandler() }

        existingHandler.beforeLLMCallHandler = BeforeLLMCallHandler { eventContext: BeforeLLMCallHandlerContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptAfterLLMCall(InterceptContext) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AfterLLMCallHandlerContext) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(interceptContext.feature.key) { ExecuteLLMHandler() }

        existingHandler.afterLLMCallHandler = AfterLLMCallHandler { eventContext: AfterLLMCallHandlerContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     *
     * @param handle A suspend lambda function that processes tool calls, taking the tool, and its arguments as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptToolCall(InterceptContext) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallHandlerContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallHandler = ToolCallHandler { eventHandler: ToolCallHandlerContext ->
            with(interceptContext.featureImpl) { handle(eventHandler) }
        }
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *        The lambda provides the tool's stage, tool instance, tool arguments, and the value that caused the validation error.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationError(InterceptContext) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolValidationError(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolValidationErrorHandlerContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolValidationErrorHandler = ToolValidationErrorHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param handle A suspend function that is invoked when a tool call fails. It provides the stage,
     *               the tool, the tool arguments, and the throwable that caused the failure.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallFailure(InterceptContext) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallFailure(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallFailureHandlerContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallFailureHandler = ToolCallFailureHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * @param handle A suspending function that defines the behavior to execute when a tool call result is intercepted.
     * The function takes as parameters the stage of the tool call, the tool being called, its arguments,
     * and the result of the tool call if available.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallResult(InterceptContext) { eventContext ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallResult(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallResultHandlerContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallResultHandler = ToolCallResultHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    //endregion Interceptors
}
