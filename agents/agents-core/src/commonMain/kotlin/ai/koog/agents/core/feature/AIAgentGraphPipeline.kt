package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.graph.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.graph.AIAgentNodeBase
import ai.koog.agents.core.feature.handler.AfterNodeHandler
import ai.koog.agents.core.feature.handler.BeforeNodeHandler
import ai.koog.agents.core.feature.handler.ExecuteNodeHandler
import ai.koog.agents.core.feature.handler.NodeAfterExecuteContext
import ai.koog.agents.core.feature.handler.NodeBeforeExecuteContext
import ai.koog.agents.features.common.config.FeatureConfig

/**
 * A pipeline class designed for AI agent execution using a graph-based strategy.
 * Utilizes a generic strategy type parameter that determines the specific graph-based approach to be applied.
 */
public class AIAgentGraphPipeline<Input, Output>: AIAgentPipeline() {

    /**
     * Map of node execution handlers registered for different features.
     * Keys are feature storage keys, values are node execution handlers.
     */
    private val executeNodeHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteNodeHandler> = mutableMapOf()

    //region Trigger Node Handlers

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param node The node that is about to be executed
     * @param context The agent context in which the node is being executed
     * @param input The input data for the node execution
     */
    public suspend fun onBeforeNode(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase<*>, input: Any?) {
        val eventContext = NodeBeforeExecuteContext(context, node, input)
        executeNodeHandlers.values.forEach { handler -> handler.beforeNodeHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The agent context in which the node was executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node execution
     */
    public suspend fun onAfterNode(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase<*>,
        input: Any?,
        output: Any?
    ) {
        val eventContext = NodeAfterExecuteContext(context, node, input, output)
        executeNodeHandlers.values.forEach { handler -> handler.afterNodeHandler.handle(eventContext) }
    }

    //endregion Trigger Node Handlers

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
        handle: suspend TFeature.(eventContext: NodeBeforeExecuteContext) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(interceptContext.feature.key) { ExecuteNodeHandler() }

        existingHandler.beforeNodeHandler = BeforeNodeHandler { eventContext: NodeBeforeExecuteContext ->
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
        handle: suspend TFeature.(eventContext: NodeAfterExecuteContext) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(interceptContext.feature.key) { ExecuteNodeHandler() }

        existingHandler.afterNodeHandler = AfterNodeHandler { eventContext: NodeAfterExecuteContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Installs a feature into the `AIAgentGraphPipeline` by configuring it with the provided settings and registering it
     * as an active feature in the pipeline.
     *
     * @param feature The `AIAgentGraphFeature` instance to be installed, representing the specific feature and its configuration requirements.
     * @param configure A lambda for setting up and customizing the feature's initial configuration.
     */
    public fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentGraphFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }
}