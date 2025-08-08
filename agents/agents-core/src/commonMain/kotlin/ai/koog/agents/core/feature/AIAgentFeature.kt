package ai.koog.agents.core.feature

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.features.common.config.FeatureConfig

/**
 * A class for Agent Feature that can be added to an agent pipeline,
 * The feature stands for providing specific functionality and configuration capabilities.
 *
 * @param Config The type representing the configuration for this feature.
 * @param TFeature The type of the feature implementation.
 */
public interface AIAgentFeature<Config : FeatureConfig, TFeature : Any> {

    /**
     * A key used to uniquely identify a feature of type [TFeature] within the local agent storage.
     */
    public val key: AIAgentStorageKey<TFeature>

    /**
     * Creates and returns an initial configuration for the feature.
     */
    public fun createInitialConfig(): Config
}

/**
 * Interface representing a specific type of AI agent feature that operates within a graph-based pipeline framework.
 * This feature is responsible for defining a strategy and providing related configurations and functionalities
 * for agents operating in a graph workflow.
 *
 * @param Config The type of the feature configuration, which must inherit from [FeatureConfig].
 * @param TFeature The type of the feature implementation.
 */
public interface AIAgentGraphFeature<Config : FeatureConfig, TFeature : Any> : AIAgentFeature<Config, TFeature> {
    /**
     * Installs the feature into the specified [AIAgentGraphPipeline].
     */
    @OptIn(InternalAgentsApi::class)
    public fun install(config: Config, pipeline: AIAgentGraphPipeline<*, *>)
}