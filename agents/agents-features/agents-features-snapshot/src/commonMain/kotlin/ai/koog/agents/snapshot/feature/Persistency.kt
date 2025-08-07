package ai.koog.agents.snapshot.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AgentContextData
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.context.removeAgentContextData
import ai.koog.agents.core.agent.context.store
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.graph.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import ai.koog.prompt.message.Message
import kotlinx.datetime.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A feature that provides checkpoint functionality for AI agents.
 *
 * This class allows saving and restoring the state of an agent at specific points during execution.
 * Checkpoints capture the agent's message history, current node, and input data, enabling:
 * - Resuming agent execution from a specific point
 * - Rolling back to previous states
 * - Persisting agent state across sessions
 *
 * The feature can be configured to automatically create checkpoints after each node execution
 * using the [PersistencyFeatureConfig.enableAutomaticPersistency] option.
 *
 * @property persistencyStorageProvider The provider responsible for storing and retrieving checkpoints
 * @property currentNodeId The ID of the node currently being executed
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, InternalAgentsApi::class)
public class Persistency(private val persistencyStorageProvider: PersistencyStorageProvider) {
    /**
     * Represents the identifier of the current node being executed within the agent pipeline.
     *
     * This property is used to track the state of the agent's execution and is updated whenever
     * the agent begins processing a new node.
     * It plays a crucial role in maintaining the agent's
     * state across checkpoints and ensuring accurate state restoration during rollbacks.
     *
     * The value is nullable, indicating that there might be no current node under execution
     * (e.g., when the pipeline is idle or has not started).
     */
    public var currentNodeId: String? = null
        private set

    /**
     * Feature companion object that implements [AIAgentFeature] for the checkpoint functionality.
     */
    public companion object Feature : AIAgentFeature<PersistencyFeatureConfig, Persistency> {
        /**
         * The storage key used to identify this feature in the agent's feature registry.
         */
        override val key: AIAgentStorageKey<Persistency>
            get() = AIAgentStorageKey("agents-features-snapshot")

        /**
         * Creates the default configuration for this feature.
         *
         * @return A new instance of [PersistencyFeatureConfig] with default settings
         */
        override fun createInitialConfig(): PersistencyFeatureConfig = PersistencyFeatureConfig()

        /**
         * Installs the checkpoint feature into the agent pipeline.
         *
         * This method sets up the necessary interceptors to:
         * - Restore the latest checkpoint when the agent starts
         * - Track the current node being executed
         * - Create checkpoints after node execution (if continuous persistence is enabled)
         *
         * @param config The configuration for the checkpoint feature
         * @param pipeline The agent pipeline to install the feature into
         */
        override fun install(
            config: PersistencyFeatureConfig,
            pipeline: AIAgentPipeline
        ) {
            val featureImpl = Persistency(config.storage)
            val interceptContext = InterceptContext(this, featureImpl)
            val context = InterceptContext(this, featureImpl)

            pipeline.interceptContextAgentFeature(this) { ctx ->
                return@interceptContextAgentFeature featureImpl
            }

            pipeline.interceptBeforeAgentStarted(interceptContext) { ctx ->
                require(ctx.strategy.metadata.uniqueNames) { "Checkpoint feature requires unique node names in the strategy metadata" }
                ctx.feature.rollbackToLatestCheckpoint(ctx.context)
            }

            pipeline.interceptStrategyStarted(context) { ctx ->
                setExecutionPointIfNeeded(ctx.agentContext, ctx.strategy)
            }

            pipeline.interceptAfterNode(interceptContext) { eventCtx ->
                if (config.enableAutomaticPersistency) {
                    createCheckpoint(
                        eventCtx.context.id,
                        eventCtx.context,
                        eventCtx.node.id,
                        eventCtx.input
                    )
                }
            }

            pipeline.interceptBeforeNode(interceptContext) { eventCtx ->
                featureImpl.currentNodeId = eventCtx.node.id
            }
        }
    }

    /**
     * Creates a checkpoint of the agent's current state.
     *
     * This method captures the agent's message history, current node, and input data
     * and stores it as a checkpoint using the configured storage provider.
     *
     * @param T The type of the input data
     * @param agentId The ID of the agent to create a checkpoint for
     * @param agentContext The context of the agent containing the state to checkpoint
     * @param nodeId The ID of the node where the checkpoint is created
     * @param lastInput The input data to include in the checkpoint
     * @param checkpointId Optional ID for the checkpoint; a random UUID is generated if not provided
     * @return The created checkpoint data
     */
    public suspend inline fun <T> createCheckpoint(
        agentId: String,
        agentContext: AIAgentContextBase<*>,
        nodeId: String,
        lastInput: T,
        checkpointId: String? = null
    ): AgentCheckpointData {
        val checkpoint = agentContext.llm.readSession {
            return@readSession AgentCheckpointData(
                checkpointId = checkpointId ?: Uuid.random().toString(),
                messageHistory = prompt.messages,
                nodeId = nodeId,
                lastInput = serializeInput(lastInput),
                agentId = agentId,
                createdAt = Clock.System.now()
            )
        }

        saveCheckpoint(checkpoint)
        return checkpoint
    }

    /**
     * Saves a checkpoint using the configured storage provider.
     *
     * @param checkpointData The checkpoint data to save
     */
    public suspend fun saveCheckpoint(checkpointData: AgentCheckpointData) {
        persistencyStorageProvider.saveCheckpoint(checkpointData)
    }

    /**
     * Retrieves the latest checkpoint for the specified agent.
     *
     * @param agentId The ID of the agent to get the latest checkpoint for
     * @return The latest checkpoint data, or null if no checkpoint exists
     */
    public suspend fun getLatestCheckpoint(agentId: String): AgentCheckpointData? =
        persistencyStorageProvider.getLatestCheckpoint(agentId)

    /**
     * Retrieves a specific checkpoint by ID for the specified agent.
     *
     * @param agentId The ID of the agent to get the checkpoint for
     * @param checkpointId The ID of the checkpoint to retrieve
     * @return The checkpoint data with the specified ID, or null if not found
     */
    public suspend fun getCheckpointById(agentId: String, checkpointId: String): AgentCheckpointData? =
        persistencyStorageProvider.getCheckpoints(agentId).firstOrNull { it.checkpointId == checkpointId }

    /**
     * Sets the execution point of an agent to a specific state.
     *
     * This method directly modifies the agent's context to force execution from a specific point,
     * with the specified message history and input data.
     *
     * @param agentContext The context of the agent to modify
     * @param nodeId The ID of the node to set as the current execution point
     * @param messageHistory The message history to set for the agent
     * @param input The input data to set for the agent
     */
    public fun setExecutionPoint(
        agentContext: AIAgentContextBase<*>,
        nodeId: String,
        messageHistory: List<Message>,
        input: Any?
    ) {
        agentContext.store(AgentContextData(messageHistory, nodeId, input))
    }

    /**
     * Rolls back an agent's state to a specific checkpoint.
     *
     * This method retrieves the checkpoint with the specified ID and, if found,
     * sets the agent's context to the state captured in that checkpoint.
     *
     * @param checkpointId The ID of the checkpoint to roll back to
     * @param agentContext The context of the agent to roll back
     * @return The checkpoint data that was restored or null if the checkpoint was not found
     */
    public suspend fun rollbackToCheckpoint(
        checkpointId: String,
        agentContext: AIAgentContextBase<*>
    ): AgentCheckpointData? {
        val checkpoint: AgentCheckpointData? = getCheckpointById(agentContext.id, checkpointId)
        if (checkpoint != null) {
            agentContext.store(checkpoint.toAgentContextData())
        }
        return checkpoint
    }

    /**
     * Rolls back an agent's state to the latest checkpoint.
     *
     * This method retrieves the most recent checkpoint for the agent and,
     * if found, sets the agent's context to the state captured in that checkpoint.
     *
     * @param agentContext The context of the agent to roll back
     * @return The checkpoint data that was restored or null if no checkpoint was found
     */
    public suspend fun rollbackToLatestCheckpoint(agentContext: AIAgentContextBase<*>): AgentCheckpointData? {
        val checkpoint: AgentCheckpointData? = getLatestCheckpoint(agentContext.id)
        if (checkpoint != null) {
            agentContext.store(checkpoint.toAgentContextData())
        }
        return checkpoint
    }
}

/**
 * Extension function to access the checkpoint feature from an agent context.
 *
 * @return The [Persistency] feature instance for this agent
 * @throws IllegalStateException if the checkpoint feature is not installed
 */
public fun AIAgentContextBase<*>.persistency(): Persistency = featureOrThrow(Persistency.Feature)

/**
 * Extension function to perform an action with the checkpoint feature.
 *
 * This is a convenience function that retrieves the checkpoint feature and
 * executes the provided action with it.
 *
 * @param T The return type of the action
 * @param context The agent context to pass to the action
 * @param action The action to perform with the checkpoint feature
 * @return The result of the action
 */
public suspend fun <T> AIAgentContextBase<*>.withPersistency(
    context: AIAgentContextBase<*>,
    action: suspend Persistency.(AIAgentContextBase<*>) -> T
): T = persistency().action(context)

@OptIn(InternalAgentsApi::class)
private suspend fun setExecutionPointIfNeeded(
    agentContext: AIAgentContextBase<*>,
    strategy: AIAgentGraphStrategy<*, *>
) {
    val additionalContextData = agentContext.getAgentContextData()
    if (additionalContextData == null) {
        return
    }

    additionalContextData.let { contextData ->
        val nodeId = contextData.nodeId
        strategy.setExecutionPoint(nodeId, contextData.lastInput)
        val messages = contextData.messageHistory
        agentContext.llm.withPrompt {
            this.withMessages { (messages).sortedBy { m -> m.metaInfo.timestamp } }
        }
    }

    agentContext.removeAgentContextData()
}