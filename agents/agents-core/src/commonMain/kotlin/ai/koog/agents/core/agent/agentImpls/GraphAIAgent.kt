@file:OptIn(ExperimentalUuidApi::class, InternalAgentsApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.agentImpls

import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.agent.AIAgentBaseImpl
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.graph.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public class GraphAIAgent<Input, Output>(
    promptExecutor: PromptExecutor,
    private val strategy: AIAgentGraphStrategy<Input, Output>,
    agentConfig: AIAgentConfigBase,
    id: String = Uuid.random().toString(),
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    clock: Clock = Clock.System,
    installFeatures: FeatureContext.() -> Unit = {},
) : AIAgentBaseImpl<Input, Output>(
    promptExecutor, agentConfig, id, toolRegistry, clock, strategy.name + "-" + id
) {
    init {
        FeatureContext(this).installFeatures()
    }

    private var isRunning = false

    private val runningMutex = Mutex()

    public class FeatureContext internal constructor(private val agent: GraphAIAgent<*, *>) {
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

    private fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        pipeline.install(feature, configure)
    }

    private val _pipeline = AIAgentGraphPipeline<Input, Output>()

    override val pipeline: AIAgentPipeline
        get() = _pipeline

    private fun createContext(input: Input): AIAgentGraphContext {
        val sessionUuid = Uuid.random()
        val runId = sessionUuid.toString()
        val stateManager = AIAgentStateManager()
        val storage = AIAgentStorage()

        // Environment (initially equal to the current agent), transformed by some features
        //   (ex: testing feature transforms it into a MockEnvironment with mocked tools)
        val preparedEnvironment =
            pipeline.transformEnvironment(
                strategy = strategy,
                agent = this@GraphAIAgent,
                baseEnvironment = this@GraphAIAgent
            )

        val agentContext = AIAgentGraphContext(
            environment = preparedEnvironment,
            agentInput = input,
            config = agentConfig,
            llm = AIAgentLLMContext(
                tools = toolRegistry.tools.map { it.descriptor },
                toolRegistry = toolRegistry,
                prompt = agentConfig.prompt,
                model = agentConfig.model,
                promptExecutor = PromptExecutorProxy(
                    executor = promptExecutor,
                    pipeline = pipeline,
                    runId = runId
                ),
                environment = preparedEnvironment,
                config = agentConfig,
                clock = clock
            ),
            stateManager = stateManager,
            storage = storage,
            runId = runId,
            strategyName = strategy.name,
            pipeline = _pipeline,
            id = id,
        )
        return agentContext
    }

    override suspend fun run(agentInput: Input): Output {
        runningMutex.withLock {
            if (isRunning) {
                throw IllegalStateException("Agent is already running")
            }

            isRunning = true
        }

        pipeline.prepareFeatures()

        val sessionUuid = Uuid.random()
        val runId = sessionUuid.toString()

        return withContext(
            AgentRunInfoContextElement(
                agentId = id,
                runId = runId,
                agentConfig = agentConfig,
                strategyName = strategy.name
            )
        ) {
            val agentContext: AIAgentGraphContext = createContext(input = agentInput)

            logger.debug { formatLog(agentId = id, runId = runId, message = "Starting agent execution") }

            pipeline.onBeforeAgentStarted(
                runId = runId,
                agent = this@GraphAIAgent,
                strategy = strategy,
                context = agentContext
            )

            var strategyResult = strategy.execute(context = agentContext, input = agentInput)
            while (strategyResult == null && agentContext.getAgentContextData() != null) {
                pipeline.onBeforeStrategyStarted(strategy, agentContext)
                strategyResult = strategy.execute(context = agentContext, input = agentInput)
            }

            logger.debug { formatLog(agentId = id, runId = runId, message = "Finished agent execution") }
            pipeline.onAgentFinished(agentId = id, runId = runId, result = strategyResult)

            runningMutex.withLock {
                isRunning = false
            }

            return@withContext strategyResult ?: error("result is null")

        }
    }

    @OptIn(ExperimentalUuidApi::class)
    public fun <Input, Output> AIAgent(
        promptExecutor: PromptExecutor,
        strategy: AIAgentGraphStrategy<Input, Output>,
        agentConfig: AIAgentConfigBase,
        id: String = Uuid.random().toString(),
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        clock: Clock = Clock.System,
        installFeatures: FeatureContext.() -> Unit = {}
    ): AIAgentBase<Input, Output> {
        return GraphAIAgent(
            promptExecutor = promptExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            id = id,
            toolRegistry = toolRegistry,
            clock = clock,
            installFeatures = installFeatures
        )
    }
}