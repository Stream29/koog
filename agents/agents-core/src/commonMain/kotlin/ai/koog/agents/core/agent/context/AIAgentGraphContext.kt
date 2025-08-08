@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.utils.RWLock
import kotlin.text.replace

/**
 * AIAgentGraphContext represents a specialized AI agent context designed for managing graph-based
 * agent pipelines, extending the capabilities provided by the generic AIAgentContext.
 *
 * This class provides the necessary infrastructure for the execution and management of AI agent
 * environments that depend on graph pipelines, supporting advanced workflows and complex operations.
 *
 * @property pipeline The graph pipeline utilized by the agent. This is marked with the InternalAgentsApi
 * annotation, signifying that it is intended for internal agent operations and should not be used
 * externally without caution.
 */
public class AIAgentGraphContext(
    override val environment: AIAgentEnvironment,
    override val agentInput: Any?,
    override val config: AIAgentConfigBase,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    override val runId: String,
    override val strategyName: String,
    override val id: String,
    override val pipeline: AIAgentGraphPipeline<*, *>
) : AIAgentGraphContextBase() {
    /**
     * Mutable wrapper for AI agent context properties.
     */
    internal class MutableAIAgentContext(
        var llm: AIAgentLLMContext,
        var stateManager: AIAgentStateManager,
        var storage: AIAgentStorage,
    ) {
        private val rwLock = RWLock()

        /**
         * Creates a copy of the current [MutableAIAgentContext].
         * @return A new instance of [MutableAIAgentContext] with copies of all mutable properties.
         */
        suspend fun copy(): MutableAIAgentContext {
            return rwLock.withReadLock {
                MutableAIAgentContext(llm.copy(), stateManager.copy(), storage.copy())
            }
        }

        /**
         * Replaces the current context with the provided context.
         * @param llm The LLM context to replace the current context with.
         * @param stateManager The state manager to replace the current context with.
         * @param storage The storage to replace the current context with.
         */
        suspend fun replace(llm: AIAgentLLMContext?, stateManager: AIAgentStateManager?, storage: AIAgentStorage?) {
            rwLock.withWriteLock {
                llm?.let { this.llm = llm }
                stateManager?.let { this.stateManager = stateManager }
                storage?.let { this.storage = storage }
            }
        }
    }

    private val mutableAIAgentContext = MutableAIAgentContext(llm, stateManager, storage)

    override val llm: AIAgentLLMContext
        get() = mutableAIAgentContext.llm

    override val storage: AIAgentStorage
        get() = mutableAIAgentContext.storage

    override val stateManager: AIAgentStateManager
        get() = mutableAIAgentContext.stateManager

    override fun copy(
        environment: AIAgentEnvironment,
        agentInput: Any?,
        config: AIAgentConfigBase,
        llm: AIAgentLLMContext,
        stateManager: AIAgentStateManager,
        storage: AIAgentStorage,
        runId: String,
        strategyId: String,
        pipeline: AIAgentGraphPipeline<*, *>
    ): AIAgentGraphContext {
        return AIAgentGraphContext(
            environment,
            agentInput,
            config,
            llm,
            stateManager,
            storage,
            runId,
            strategyId,
            id,
            pipeline
        )
    }

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    override suspend fun replace(context: AIAgentGraphContextBase) {
        mutableAIAgentContext.replace(
            context.llm,
            context.stateManager,
            context.storage
        )
    }

    /**
     * Creates a copy of the current [AIAgentContext] with deep copies of all mutable properties.
     *
     * @return A new instance of [AIAgentContext] with copies of all mutable properties.
     */
    override suspend fun fork(): AIAgentGraphContext = copy(
        llm = this.llm.copy(),
        storage = this.storage.copy(),
        stateManager = this.stateManager.copy(),
    )

    override fun features(): Map<AIAgentStorageKey<*>, Any> {
        return pipeline.getAgentFeatures(this)
    }
}

/**
 * Abstract base class for AI agent graph contexts, extending [AIAgentContext] with functionality
 * specific to graph-based AI agent pipelines. This class serves as the foundation for managing
 * graph-based execution contexts for AI agents.
 */
public abstract class AIAgentGraphContextBase: AIAgentContext<AIAgentGraphPipeline<*, *>>() {

    /**
     * Creates a copy of the current [AIAgentContext] with deep copies of all mutable properties.
     * This method is particularly useful in scenarios like parallel node execution
     * where contexts need to be sent to different threads and then merged back together using [replace].
     *
     * @return A new instance of [AIAgentContext] with copies of all mutable properties.
     */
    public abstract suspend fun fork(): AIAgentGraphContextBase

    /**
     * Replaces the current context with the provided context.
     * This method is used to update the current context with values from another context,
     * particularly useful in scenarios like parallel node execution where contexts need to be merged.
     *
     * @param context The context to replace the current context with.
     */
    public abstract suspend fun replace(context: AIAgentGraphContextBase)

}