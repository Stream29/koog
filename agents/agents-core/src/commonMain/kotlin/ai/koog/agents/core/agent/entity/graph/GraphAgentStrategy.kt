package ai.koog.agents.core.agent.entity.graph

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentGraphContext
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.utils.runCatchingCancellable

/**
 * Represents a strategy for managing and executing AI agent workflows built as subgraphs of interconnected nodes.
 *
 * @property name The unique identifier for the strategy.
 * @property nodeStart The starting node of the strategy, initiating the subgraph execution.
 * By default, the start node gets the agent input and returns
 * @property nodeFinish The finishing node of the strategy, marking the subgraph's endpoint.
 * @property toolSelectionStrategy The strategy responsible for determining the toolset available during subgraph execution.
 */
public class AIAgentGraphStrategy<Input, Output>(
    override val name: String,
    public val nodeStart: StartNode<Input>,
    public val nodeFinish: FinishNode<Output>,
    toolSelectionStrategy: ToolSelectionStrategy
) : AIAgentStrategy<Input, Output, AIAgentGraphContext>, AIAgentSubgraph<Input, Output>(
    name, nodeStart, nodeFinish, toolSelectionStrategy
) {
    /**
     * Represents the metadata of the subgraph associated with the AI agent strategy.
     *
     * This variable holds essential information about the structure and properties of the
     * subgraph, such as the mapping of node names to their associated implementations and
     * the uniqueness of node names within the subgraph.
     *
     * This property can only be set internally, and an attempt to access it before initialization
     * will result in an `IllegalStateException`.
     */
    public lateinit var metadata: SubgraphMetadata

    @OptIn(InternalAgentsApi::class)
    override suspend fun execute(context: AIAgentGraphContext, input: Input): Output? {
        return runCatchingCancellable {
            val result = super.execute(context = context, input = input)
            context.pipeline.onStrategyFinished(this, context, result)
            result
        }.onFailure {
            context.environment.reportProblem(it)
        }.getOrThrow()
    }

    /**
     * Finds and sets the node for the strategy based on the provided context.
     */
    public fun setExecutionPoint(nodeId: String, input: Any?) {
        val fullPath = metadata.nodesMap.keys.firstOrNull {
            val segments = it.split(":")
            segments.last() == nodeId
        } ?: throw IllegalArgumentException("Node $nodeId not found")

        val segments = fullPath.split(":")
        if (segments.isEmpty())
            throw IllegalArgumentException("Invalid node path: $fullPath")

        val strategyName = segments.firstOrNull() ?: return

        // getting the very first segment (it should be a root strategy node)
        var currentNode: AIAgentNodeBase<*, *>? = metadata.nodesMap[strategyName]
        var currentPath = strategyName

        // restoring the current node for each subgraph including strategy
        val segmentsInbetween = segments.drop(1).dropLast(1)
        for (segment in segmentsInbetween) {
            currentNode as? ExecutionPointNode
                ?: throw IllegalStateException("Node ${currentNode?.name} does not have subnodes")

            currentPath = "$currentPath:$segment"
            val nextNode = metadata.nodesMap[currentPath]
            if (nextNode is ExecutionPointNode) {
                currentNode.enforceExecutionPoint(nextNode, input)
                currentNode = nextNode
            }
        }

        // forcing the very last segment to the latest pre-leaf node to complete the chain
        val leaf = metadata.nodesMap[fullPath] ?: throw IllegalStateException("Node ${segments.last()} not found")
        leaf.let {
            currentNode as? ExecutionPointNode
                ?: throw IllegalStateException("Node ${currentNode?.name} does not have subnodes")
            currentNode.enforceExecutionPoint(it, input)
        }
    }
}