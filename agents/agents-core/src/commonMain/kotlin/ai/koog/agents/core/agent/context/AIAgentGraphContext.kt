@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentPipeline

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
    environment: AIAgentEnvironment,
    agentInput: Any?,
    config: AIAgentConfigBase,
    llm: AIAgentLLMContext,
    stateManager: AIAgentStateManager,
    storage: AIAgentStorage,
    runId: String,
    strategyName: String,
    id: String,
    override val pipeline: AIAgentGraphPipeline<*, *>
) : AIAgentContext<AIAgentGraphPipeline<*, *>>(
    environment,
    agentInput,
    config,
    llm,
    stateManager,
    storage,
    runId,
    strategyName, id
) {
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
     * Creates a copy of the current [AIAgentContext] with deep copies of all mutable properties.
     *
     * @return A new instance of [AIAgentContext] with copies of all mutable properties.
     */
    override suspend fun fork(): AIAgentGraphContext = copy(
        llm = this.llm.copy(),
        storage = this.storage.copy(),
        stateManager = this.stateManager.copy(),
    )
}