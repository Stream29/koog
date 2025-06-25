package ai.koog.agents.core.agent.entity

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.NodeNameContextElement
import ai.koog.agents.core.annotation.InternalAgentsApi
import kotlinx.coroutines.withContext

/**
 * Represents an abstract node in an AI agent strategy graph, responsible for executing a specific
 * operation and managing directed edges to other nodes.
 *
 * @param Input The type of input data this node processes.
 * @param Output The type of output data this node produces.
 */
public abstract class AIAgentNodeBase<Input, Output> internal constructor() {
    /**
     * The name of the AI agent node.
     * This property serves as a unique identifier for the node within the strategy graph
     * and is used to distinguish and reference nodes in the graph structure.
     */
    public abstract val name: String

    /**
     * Represents the directed edges connecting the current node in the AI agent strategy graph
     * to other nodes. Each edge defines the flow and transformation of output data
     * from this node to another.
     *
     * The list is initially empty and can only be modified internally by using the
     * [addEdge] function, which appends new edges to the existing list.
     *
     * @property edges A list of [AIAgentEdge] describing the connections from this node
     * to other nodes in the strategy graph.
     */
    public var edges: List<AIAgentEdge<Output, *>> = emptyList()
        private set

    /**
     * Adds a directed edge from the current node, enabling connections between this node
     * and other nodes in the AI agent strategy graph.
     *
     * @param edge The edge to be added, representing a connection from this node's output
     * to another node in the strategy graph.
     */
    public open fun addEdge(edge: AIAgentEdge<Output, *>) {
        edges = edges + edge
    }

    /**
     * Represents a resolved edge in the context of an AI agent strategy graph, combining an edge and
     * its corresponding resolved output.
     *
     * @property edge The directed edge that connects different nodes within the AI agent strategy graph.
     * This edge signifies a pathway for data flow between nodes.
     * @property output The resolved output associated with the provided edge. This represents
     * the data produced or passed along this edge during execution.
     */
    public data class ResolvedEdge(val edge: AIAgentEdge<*, *>, val output: Any?)

    /**
     * Resolves the edge associated with the provided node output and execution context.
     * Iterates through available edges and identifies the first edge that can successfully
     * process the given node output within the provided context. If a resolvable edge is found,
     * it returns a `ResolvedEdge` containing the edge and its output. Otherwise, returns null.
     *
     * @param context The execution context in which the edge is resolved.
     * @param nodeOutput The output of the current node used to resolve the edge.
     * @return A `ResolvedEdge` containing the matched edge and its output, or null if no edge matches.
     */
    public suspend fun resolveEdge(
        context: AIAgentContextBase,
        nodeOutput: Output
    ): ResolvedEdge? {
        for (currentEdge in edges) {
            val output = currentEdge.forwardOutputUnsafe(nodeOutput, context)

            if (!output.isEmpty) {
                return ResolvedEdge(currentEdge, output.value)
            }
        }

        return null
    }

    /**
     * @suppress
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun resolveEdgeUnsafe(context: AIAgentContextBase, nodeOutput: Any?): ResolvedEdge? =
        resolveEdge(context, nodeOutput as Output)

    /**
     * Executes a specific operation based on the given context and input.
     *
     * @param context The execution context that provides necessary runtime information and functionality.
     * @param input The input data required to perform the execution.
     * @return The result of the execution as an Output object.
     */
    public abstract suspend fun execute(context: AIAgentContextBase, input: Input): Output

    /**
     * TODO: SD -- fix
     */
    @Suppress("UNCHECKED_CAST")
    @InternalAgentsApi
    public suspend fun executeUnsafe(context: AIAgentContextBase, input: Any?): Any? {

        return withContext(NodeNameContextElement(name)) {
            context.pipeline.onBeforeNode(context = context, node = this@AIAgentNodeBase, input = input)
            val output = execute(context, input as Input)
            context.pipeline.onAfterNode(context = context, node = this@AIAgentNodeBase, input = input, output = output)

            return@withContext output
        }
    }
}

/**
 * Represents a simple implementation of an AI agent node, encapsulating a specific execution
 * logic that processes the input data and produces an output.
 *
 * @param Input The type of input data this node processes.
 * @param Output The type of output data this node produces.
 * @property name The name of the node, used for identification and debugging.
 * @property execute A suspending function that defines the execution logic for the node. It
 * processes the provided input within the given execution context and produces an output.
 */
internal class AIAgentNode<Input, Output> internal constructor(
    override val name: String,
    val execute: suspend AIAgentContextBase.(input: Input) -> Output
) : AIAgentNodeBase<Input, Output>() {
    override suspend fun execute(context: AIAgentContextBase, input: Input): Output = context.execute(input)
}

/**
 * Represents the base node for starting a subgraph in an AI agent strategy graph.
 * Derived from [AIAgentNodeBase], this node acts as an entry point for executing subgraphs
 * identified by a unique name.
 *
 * @param Input The type of input data this node processes and produces as output.
 */
public open class AIAgentStartNodeBase<Input>() : AIAgentNodeBase<Input, Input>() {
    /**
     * The name of the subgraph associated with the AI agent's starting node.
     *
     * This property serves as an identifier or label for the subgraph, helping to distinguish
     * and reference specific AI workflows or strategies. It can be null if no subgraph
     * name has been assigned.
     *
     * Internal changes to this property are restricted to ensure controlled modification, as the setter
     * is marked internal. Its value may influence how the AI agent node generates its unique name.
     */
    public var subgraphName: String? = null
        internal set

    override val name: String get() = subgraphName?.let { "__start__$it" } ?: "__start__"

    override suspend fun execute(context: AIAgentContextBase, input: Input): Input = input
}

/**
 * Represents a specialized node within an AI agent strategy graph that marks the endpoint
 * of a subgraph. This node serves as a "finish" node and directly passes its input
 * to its output without modification, acting as an identity operation.
 *
 * @param Output The type of data this node processes and produces.
 */
public open class AIAgentFinishNodeBase<Output>() : AIAgentNodeBase<Output, Output>() {
    /**
     * Stores the name of the subgraph associated with the node.
     *
     * This variable is used to identify or tag subgraphs in AI agent strategies.
     * If `subgraphName` is set, it will be integrated into the node's identity or behavior,
     * such as forming a unique name for the node.
     *
     * This property is mutable within the internal scope, but read-only from external scopes.
     */
    public var subgraphName: String? = null
        internal set

    override val name: String = subgraphName?.let { "__finish__$it" } ?: "__finish__"

    override fun addEdge(edge: AIAgentEdge<Output, *>) {
        throw IllegalStateException("FinishSubgraphNode cannot have outgoing edges")
    }

    override suspend fun execute(context: AIAgentContextBase, input: Output): Output = input
}

/**
 * Represents the starting node in an AI agent's graph structure.
 *
 * This node serves as the initial entry point of execution within the strategy.
 * It inherits behavior from `StartAIAgentNodeBase` and uses `String` as the input
 * type. The `StartNode` is responsible for initiating the subgraph where it resides.
 *
 * The `name` property of the node reflects a uniquely identifiable pattern using
 * the prefix "__start__" and the optional subgraph name, enabling traceability of
 * execution flow in multi-subgraph setups.
 *
 * This node effectively passes its input as-is to the next node in the execution
 * pipeline, allowing downstream nodes to transform or handle the data further.
 */
internal class StartNode internal constructor() : AIAgentStartNodeBase<String>()

/**
 * A specialized implementation of [FinishNode] that finalizes the execution of an AI agent subgraph.
 *
 * This object represents the terminal node within a subgraph structure that returns the final output.
 * It is parameterized to work with output data of type `String`.
 *
 * The [FinishNode] enforces the following constraints:
 * - It cannot have outgoing edges, meaning no further nodes can follow it in the execution graph.
 * - It simply returns the input it receives as its output, ensuring no modification occurs at the end of execution.
 *
 * This node is critical to denote the completion of localized processing within a subgraph context.
 */
internal class FinishNode internal constructor() : AIAgentFinishNodeBase<String>()

