package ai.koog.agents.testing.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.entity.*
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.BaseBuilder
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.agents.testing.tools.MockEnvironment
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.Tokenizer
import kotlinx.datetime.Clock
import org.jetbrains.annotations.TestOnly

/**
 * DummyAgentContext is an implementation of the AIAgentContextBase interface used primarily for testing or mocking purposes.
 * It is constructed using an instance of AIAgentContextMockBuilder, which allows configuring the underlying mocked components.
 *
 * This class provides access to various components of an AI agent's context, such as environment, input, configuration,
 * language model, state manager, storage, session information, and strategy ID. Each component is based on the fields
 * defined in the provided builder instance.
 *
 * Features:
 * - Access to predefined mocked components.
 * - Customizable behavior through the AIAgentContextMockBuilder interface.
 * - Throws NotImplementedError if the requested component is not initialized in the builder.
 */
public class DummyAgentContext(
    private val builder: AIAgentContextMockBuilder,
) : AIAgentContextBase {
    /**
     * Indicates whether a Language Learning Model (LLM) is defined in the current context.
     *
     * This property is true if the LLM context (`llm`) has been initialized, providing functionality for handling
     * LLM-related operations within the agent context. It can be used for conditional logic to verify if LLM-specific
     * capabilities are available.
     */
    public val isLLMDefined: Boolean = builder.llm != null
    /**
     * Indicates whether the environment for the agent context is defined.
     *
     * This property evaluates to `true` if the `environment` property in the builder
     * is not null, meaning that a specific environment has been explicitly set for
     * the agent context. If `false`, it implies that no environment has been
     * defined and a default or mock environment may be used in its place.
     */
    public val isEnvironmentDefined: Boolean = builder.environment != null

    override val environment: AIAgentEnvironment
        get() = builder.environment
            ?: throw NotImplementedError("Environment is not mocked")

    override val agentInput: String
        get() = builder.agentInput
            ?: throw NotImplementedError("Config is not mocked")

    override val config: AIAgentConfigBase
        get() = builder.config
            ?: throw NotImplementedError("Config is not mocked")

    override val llm: AIAgentLLMContext
        get() = builder.llm
            ?: throw NotImplementedError("LLM is not mocked")

    override val stateManager: AIAgentStateManager
        get() = builder.stateManager
            ?: throw NotImplementedError("State manager is not mocked")

    override val storage: AIAgentStorage
        get() = builder.storage
            ?: throw NotImplementedError("Storage is not mocked")

    override val sessionId: String
        get() = builder.sessionId
            ?: throw NotImplementedError("Session UUID is not mocked")

    override val strategyName: String
        get() = builder.strategyName
            ?: throw NotImplementedError("Strategy ID is not mocked")

    /**
     * @suppress
     */
    @InternalAgentsApi
    override val pipeline: AIAgentPipeline = AIAgentPipeline()

    override fun <Feature : Any> feature(key: AIAgentStorageKey<Feature>): Feature? =
        throw NotImplementedError("feature() getting in runtime is not supported for mock")

    override fun <Feature : Any> feature(feature: AIAgentFeature<*, Feature>): Feature? =
        throw NotImplementedError("feature()  getting in runtime is not supported for mock")

    override fun copy(
        environment: AIAgentEnvironment?,
        agentInput: String?,
        config: AIAgentConfigBase?,
        llm: AIAgentLLMContext?,
        stateManager: AIAgentStateManager?,
        storage: AIAgentStorage?,
        pipeline: AIAgentPipeline?
    ): AIAgentContextBase = DummyAgentContext(
        builder.copy().apply {
            environment?.let { this.environment = it }
            agentInput?.let { this.agentInput = it }
            config?.let { this.config = it }
            llm?.let { this.llm = it }
            stateManager?.let { this.stateManager = it }
            storage?.let { this.storage = it }
        }
    )
}

/**
 * A base interface for building mock implementations of the [AIAgentContextBase] interface.
 *
 * This interface provides configurable properties and methods for creating a mock
 * AI agent context, enabling the customization of its environment, input, configuration,
 * state management, and more. It is intended for use in testing scenarios and allows
 * for the creation of testable mock instances of AI agent contexts.
 *
 * Extends the [BaseBuilder] interface for constructing instances of type [AIAgentContextBase].
 */
@TestOnly
public interface AIAgentContextMockBuilderBase : BaseBuilder<AIAgentContextBase> {
    /**
     * Represents the environment used by the AI agent to interact with external systems.
     *
     * This variable enables the association of an AI agent's context with a specific instance of
     * `AIAgentEnvironment`. The `AIAgentEnvironment` interface provides mechanisms for tool execution,
     * problem reporting, and termination signaling, serving as the bridge between the agent and its surrounding environment.
     *
     * When set, this variable allows the agent to execute tools through the connected environment, as well as
     * handle exceptions and send termination messages as needed during operation.
     *
     * The value can be null, indicating that no environment is currently associated with the context.
     *
     * @see AIAgentEnvironment
     */
    public var environment: AIAgentEnvironment?
    /**
     * Represents the input to be used by the AI agent during its execution.
     * This variable can be set to define specific data or context relevant to the agent's task.
     * It is nullable, indicating that the agent may operate without an explicitly defined input.
     */
    public var agentInput: String?
    /**
     * Specifies the configuration for the AI agent.
     *
     * This property allows setting an instance of [AIAgentConfigBase], which defines various parameters
     * and settings for the AI agent, such as the model, prompt structure, execution strategies, and constraints.
     * It provides the core configuration required for the agent's setup and behavior.
     *
     * If null, the configuration will not be explicitly defined, and the agent may rely on default or externally
     * supplied configurations.
     */
    public var config: AIAgentConfigBase?
    /**
     * Represents the LLM context associated with an AI agent during testing scenarios.
     * This variable is used to configure and manage the context for an AI agent's
     * large language model (LLM) interactions, including tools, prompt handling,
     * and model-specific attributes.
     *
     * This is part of the mock building process to facilitate controlled testing of agent behaviors
     * and interactions with LLMs.
     *
     * @see AIAgentLLMContext
     */
    public var llm: AIAgentLLMContext?
    /**
     * Represents an optional state manager for an AI agent in the context of building its mock environment.
     * The `stateManager` is responsible for maintaining and managing the internal state of the agent in
     * a thread-safe manner, ensuring consistency during state modifications.
     *
     * This property can be used to define a customized or mocked state management behavior when setting
     * up the test environment for the AI agent. If left unset, no state management behavior is applied
     * in the mock context.
     */
    public var stateManager: AIAgentStateManager?
    /**
     * Represents a concurrent-safe key-value storage used for managing data within the context of mock
     * AI agent construction. This property typically holds an optional instance of [AIAgentStorage],
     * which provides methods to store, retrieve, and manage typed key-value pairs in a thread-safe manner.
     *
     * The storage is designed to facilitate the sharing of structured and unstructured data within
     * different components of the AI agent during the testing and building phase.
     *
     * The storage can be configured or accessed to simulate various testing scenarios or to mock
     * internal states of the agent.
     */
    public var storage: AIAgentStorage?
    /**
     * Represents the unique identifier associated with the session in the mock builder context.
     *
     * This property is optional and may be used to specify or retrieve the UUID that ties the
     * session to a specific context or operation.
     */
    public var sessionId: String?
    /**
     * Represents the identifier of a strategy to be used within the context of an AI agent.
     *
     * This variable allows specifying or retrieving the unique identifier associated with a
     * particular strategy. It can be null if no strategy is defined or required.
     */
    public var strategyName: String?

    /**
     * Creates and returns a copy of the current instance of `AIAgentContextMockBuilderBase`.
     *
     * @return A new instance of `AIAgentContextMockBuilderBase` with the same properties as the original.
     */
    public fun copy(): AIAgentContextMockBuilderBase

    /**
     * Builds and returns an instance of [AIAgentContextBase] based on the current properties
     * of the builder. This method creates a finalized AI agent context, integrating all the
     * specified configurations, environment settings, and components into a coherent context
     * object ready for use.
     *
     * @return A fully constructed [AIAgentContextBase] instance representing the configured agent context.
     */
    override fun build(): AIAgentContextBase
}

/**
 * AIAgentContextMockBuilder is a builder class for constructing a mock implementation of an AI agent context.
 * It provides mechanisms to configure various components of the AI agent context before constructing it.
 * This class is intended for use in testing scenarios and extends `AIAgentContextMockBuilderBase`.
 */
@TestOnly
public class AIAgentContextMockBuilder() : AIAgentContextMockBuilderBase {
    /**
     * Represents the AI agent's environment in which the context is being executed.
     *
     * This property allows setting or retrieving the `AIAgentEnvironment` instance associated with
     * the builder, enabling the integration of external environments. The environment provides
     * mechanisms for tool execution, error reporting, and communication of termination signals.
     *
     * It is nullable, as the environment may not always be defined during initialization.
     *
     * @see AIAgentEnvironment
     */
    override var environment: AIAgentEnvironment? = null
    /**
     * Represents the agent's input data used in constructing or testing the agent's context.
     *
     * This property is optional and can be null, indicating that no specific input is provided for the agent.
     * It is utilized during the construction or copying of an agent's context to define the data the agent operates on.
     */
    override var agentInput: String? = null
    /**
     * Represents the AI agent configuration used in the mock builder.
     *
     * This property holds the agent's configuration, which may include the parameters for prompts,
     * the language model to be used, iteration limits, and strategies for handling missing tools.
     * It is utilized in constructing or copying an AI agent context during testing or mock setup.
     *
     * The configuration, represented by [AIAgentConfigBase], can be modified or replaced
     * depending on the requirements of the mock or testing scenario. A `null` value indicates
     * the absence of a specific configuration.
     */
    override var config: AIAgentConfigBase? = null
    /**
     * Represents the context for accessing and managing an AI agent's LLM (Large Language Model) configuration
     * and behavior. The `llm` property allows you to define or override the LLM context for the agent,
     * including tools, prompt handling, and interaction with external dependencies.
     *
     * Can be used for dependency injection, mock testing, or modifying the LLM behavior dynamically during
     * runtime. If set to `null`, it indicates that no specific LLM context is defined, and defaults or
     * fallback mechanisms may be utilized by the containing class.
     */
    override var llm: AIAgentLLMContext? = null
    /**
     * An overrideable property for managing the agent's state using an instance of [AIAgentStateManager].
     *
     * The `stateManager` provides thread-safe mechanisms to update, lock, and access the internal
     * state of the AI agent. It ensures the consistency of state modifications and employs a
     * mutual exclusion mechanism to synchronize coroutines accessing the state.
     *
     * This property can be used for customizing state management within the context of the
     * `AIAgentContextMockBuilder` and its associated operations such as copying or building
     * mock agent contexts.
     *
     * By default, it is initialized to `null` and can be set or overridden to integrate a
     * specific `AIAgentStateManager` instance for managing agent state in custom scenarios.
     */
    override var stateManager: AIAgentStateManager? = null
    /**
     * Represents a concurrent-safe key-value storage instance for an AI agent.
     *
     * This property holds a reference to an optional [AIAgentStorage] implementation, which enables the
     * handling of typed keys and respective values in a thread-safe manner within the agent context.
     * The storage can be used to store, retrieve, or manage custom data uniquely identified by specific keys.
     *
     * It can be configured or overridden during the agent context setup or through subsequent modifications
     * to the context builder. If not provided, the default value remains `null`.
     */
    override var storage: AIAgentStorage? = null
    /**
     * Defines the unique identifier for the session context within the agent's lifecycle.
     * This property can be used to correlate and differentiate multiple sessions for the same agent
     * or across different agents.
     *
     * The `sessionUuid` can be null, indicating that the session has not been associated with an identifier.
     */
    override var sessionId: String? = null
    /**
     * Represents the identifier for the strategy to be used in the agent context.
     *
     * This property is used to distinguish and configure different strategies within an AI agent's
     * workflow. It may determine how the agent processes inputs, selects methodologies, or executes
     * tasks, depending on the applied strategy.
     *
     * Can be null if a strategy is not explicitly defined or required.
     */
    override var strategyName: String? = null

    /**
     * Creates and returns a new copy of the current `AIAgentContextMockBuilder` instance.
     * The copied instance contains the same state and configuration as the original.
     *
     * @return a new `AIAgentContextMockBuilder` instance with the same properties as the original.
     */
    override fun copy(): AIAgentContextMockBuilder {
        return AIAgentContextMockBuilder().also {
            it.environment = environment
            it.agentInput = agentInput
            it.config = config
            it.llm = llm
            it.stateManager = stateManager
            it.storage = storage
        }
    }

    /**
     * Builds and returns an instance of `DummyAgentContext`.
     *
     * This method finalizes the current configuration of the `AIAgentContextMockBuilder` instance
     * by creating a copy of its current state and passing it to the constructor of `DummyAgentContext`.
     *
     * @return A `DummyAgentContext` instance initialized with the current state of the builder.
     */
    override fun build(): DummyAgentContext {
        return DummyAgentContext(this.copy())
    }

    /**
     * Companion object providing utility methods for the encompassing class.
     */
    private companion object {
        /**
         * Creates a dummy proxy implementation of the specified type [T] using the provided name.
         *
         * The returned proxy is an instance of [T] that contains stubbed behavior for unimplemented
         * properties and methods. It is primarily intended for mocking or testing purposes.
         *
         * @param name A unique name for the proxy to associate with its string representation and errors.
         * @return A dummy proxy of type [T] with the provided name.
         */
        @Suppress("UNCHECKED_CAST")
        private inline fun <reified T : Any> createDummyProxy(name: String): T {
            return ProxyHandler<T>(name).createProxy()
        }
    }

    /**
     * ProxyHandler is a utility class that dynamically creates a proxy object of a specified type.
     * The proxy implements default behavior for methods such as `toString`, `equals`, property access,
     * and method invocation, providing a placeholder implementation.
     *
     * This class is typically used to generate mock or dummy objects for testing or prototyping purposes.
     *
     * @param T The type of the proxy object to be created. Must be a non-abstract class.
     * @param name A string identifier associated with the proxy. This is used in placeholder implementation
     *             to display information about the proxy.
     */
    public class ProxyHandler<T : Any>(private val name: String) {
        /**
         * Creates a proxy instance of type [T]. The proxy is a dummy implementation that provides default
         * behavior for overridden methods such as `toString`, `equals`, and unimplemented operations.
         *
         * @return A proxy instance of type [T] with default, unimplemented functionality.
         */
        @Suppress("UNCHECKED_CAST")
        public fun createProxy(): T {
            return object : Any() {
                /**
                 * Returns a string representation of the object with the format `DummyProxy<name>`.
                 *
                 * This implementation provides a simple description of the proxy object that includes
                 * the name of the proxy. It is useful for debugging or logging purposes to identify
                 * the specific proxy instance by its name.
                 *
                 * @return A string in the format `DummyProxy<name>`.
                 */
                override fun toString() = "DummyProxy<${name}>"

                /**
                 * Checks whether this instance is equal to the specified object.
                 *
                 * @param other The object to compare with this instance.
                 * @return True if this instance is the same as the specified object, otherwise false.
                 */
                override fun equals(other: Any?): Boolean {
                    return this === other
                }

                /**
                 * Retrieves the value of a property specified by its name.
                 * This operator is intended to simulate property access in a proxy-like manner.
                 *
                 * @param propertyName The name of the property to retrieve.
                 * @return The value of the specified property, or null.
                 * @throws IllegalStateException This function always throws an exception indicating unimplemented property access.
                 */
                @Suppress("UNUSED_PARAMETER")
                operator fun get(propertyName: String): Any? {
                    error("Unimplemented property access: $name.$propertyName")
                }

                /**
                 * Invokes a method by its name and passes the provided arguments.
                 *
                 * This method is typically used to simulate an unimplemented method call, throwing an error
                 * to indicate that the called method is not yet implemented.
                 *
                 * @param methodName The name of the method to invoke.
                 * @param args The arguments to pass to the method, provided as a vararg of any type.
                 * @return This function does not return a value as it throws an error instead.
                 */
                @Suppress("UNUSED_PARAMETER")
                fun invoke(methodName: String, vararg args: Any?): Any? {
                    error("Unimplemented method call: $name.$methodName(${args.joinToString()})")
                }
            } as T
        }
    }
}


/**
 * Represents a reference to a specific type of node within an AI agent subgraph. This sealed class
 * provides a way to resolve and identify nodes for various processing operations in the context
 * of a subgraph.
 *
 * @param Input The input data type handled by the related node.
 * @param Output The output data type produced by the related node.
 */
@TestOnly
public sealed class NodeReference<Input, Output> {
    /**
     * Resolves the current node reference within the context of the provided AI agent subgraph.
     *
     * @param subgraph An instance of [AIAgentSubgraph], representing the structured subgraph within the AI agent workflow.
     *                 It contains the logical segment of processing, including the starting and finishing nodes.
     * @return An instance of [AIAgentNodeBase], representing the resolved node within the subgraph that corresponds
     *         to the current node reference.
     */
    public abstract fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Output>

    /**
     * The `Start` class is a specialized type of `NodeReference` that serves as a reference to
     * the starting node of an `AIAgentSubgraph`. This node initiates the processing of the subgraph
     * by resolving and returning the subgraph's starting node.
     *
     * @param Input The type of input data that this node reference accepts and processes.
     */
    public class Start<Input> : NodeReference<Input, Input>() {
        /**
         * Resolves the starting node of the given subgraph and casts it to the appropriate type.
         *
         * @param subgraph The subgraph from which the starting node will be resolved.
         * @return The starting node of the subgraph cast to AIAgentNodeBase<Input, Input>.
         */
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Input> =
            subgraph.start as AIAgentNodeBase<Input, Input>
    }

    /**
     * Represents the final node in an AI agent subgraph workflow.
     * The `Finish` node signifies the end of the processing within the subgraph and provides the result
     * of the subgraph's operations.
     *
     * @param Output The type of output data produced by this finish node.
     */
    public class Finish<Output> : NodeReference<Output, Output>() {
        /**
         * Resolves and returns the finishing node of the given AI agent subgraph.
         *
         * @param subgraph The AI agent subgraph from which the finishing node is resolved.
         * @return The finishing node of the subgraph, cast to the expected type `AIAgentNodeBase<Output, Output>`.
         */
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Output, Output> =
            subgraph.finish as AIAgentNodeBase<Output, Output>
    }

    /**
     * [NamedNode] is a class that references a specific node within a subgraph by name.
     * This class allows resolving a node instance by its unique name within a given subgraph.
     *
     * @param Input The type of input accepted by the node.
     * @param Output The type of output produced by the node.
     * @property name The unique name of the target node within the subgraph.
     */
    public open class NamedNode<Input, Output>(public val name: String) : NodeReference<Input, Output>() {
        /**
         * Resolves a node within the given AI Agent subgraph by searching for a node that matches the current node's name.
         * The method performs a depth-first traversal starting from the subgraph's start node.
         *
         * @param subgraph The AI Agent subgraph to traverse in search of the matching node.
         * @return The resolved node that matches the current node's name of type [AIAgentNodeBase<Input, Output>].
         * @throws IllegalArgumentException If no node with the specified name is found within the subgraph.
         */
        @Suppress("UNCHECKED_CAST")
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentNodeBase<Input, Output> {
            val visited = mutableSetOf<String>()
            fun visit(node: AIAgentNodeBase<*, *>): AIAgentNodeBase<Input, Output>? {
                if (node is FinishAIAgentNodeBase) return null
                if (visited.contains(node.name)) return null
                visited.add(node.name)
                if (node.name == name) return node as? AIAgentNodeBase<Input, Output>
                return node.edges.firstNotNullOfOrNull { visit(it.toNode) }
            }

            val result = visit(subgraph.start).also {
                println("Visited nodes: ${visited.joinToString()} [${visited.size}]")
            }
                ?: throw IllegalArgumentException("Node with name '$name' not found")

            return result
        }
    }

    /**
     * Represents a specialized type of node used in the context of AI agent subgraphs.
     * This class extends the functionality of a named node by providing specific behavior
     * to resolve itself as a valid subgraph within the overarching system.
     *
     * @param Input The input type that this node expects.
     * @param Output The output type that this node produces.
     * @param name The name of the node, inherited from the parent class, which serves as its identifier within the graph.
     */
    public open class SubgraphNode<Input, Output>(name: String) : NamedNode<Input, Output>(name) {
        /**
         * Resolves the specified subgraph into a strongly typed subgraph of the expected input and output types.
         * This method ensures the resolved subgraph matches the type constraints of the current node.
         *
         * @param subgraph The subgraph to resolve. This represents a structured portion of an AI agent workflow.
         * @return The resolved subgraph of type `AIAgentSubgraph<Input, Output>`.
         * @throws IllegalArgumentException If the resolved subgraph does not match the expected type constraints.
         */
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentSubgraph<Input, Output> {
            val result = super.resolve(subgraph)

            if (result !is AIAgentSubgraph<Input, Output>) {
                throw IllegalArgumentException("Node with name '$name' is not a subgraph")
            }

            return result
        }
    }

    /**
     * Represents a specific strategy within an AI agent subgraph.
     *
     * The `Strategy` class is a specialization of the `SubgraphNode` class designed to focus on
     * resolving and managing `AIAgentStrategy` instances. It provides a mechanism to ensure the
     * resolution process evaluates to the expected strategy type, preserving type safety
     * and logical consistency within the graph-based AI workflow.
     *
     * @param name The unique identifier for the strategy.
     */
    public class Strategy(name: String) : SubgraphNode<String, String>(name) {
        /**
         * Resolves the given subgraph into an `AIAgentStrategy` instance to ensure that the resolved object
         * matches the expected strategy name and type.
         *
         * @param subgraph The subgraph to be resolved. It must have the same name as the current strategy instance
         *                 and must also be of type `AIAgentStrategy`.
         * @return The resolved subgraph as an instance of `AIAgentStrategy`.
         * @throws IllegalArgumentException If the subgraph's name does not match the name of the current strategy.
         * @throws IllegalStateException If the subgraph is not of type `AIAgentStrategy`.
         */
        override fun resolve(subgraph: AIAgentSubgraph<*, *>): AIAgentStrategy {
            if (subgraph.name != name) {
                throw IllegalArgumentException("Strategy with name '$name' was expected")
            }

            if (subgraph !is AIAgentStrategy) {
                throw IllegalStateException("Resolving a strategy is not possible from a subgraph")
            }

            return subgraph
        }
    }
}

/**
 * Represents a set of assertions for validating the structure and behavior of a graph-based system.
 *
 * @property name The name or identifier of the graph being asserted.
 * @property start The starting node reference of the graph.
 * @property finish The finishing node reference of the graph.
 * @property nodes A map containing all nodes of the graph, indexed by their names.
 * @property nodeOutputAssertions A list of assertions verifying the output of specific nodes based on given inputs and contexts.
 * @property edgeAssertions A list of assertions validating the edges between nodes in the graph for specific contexts.
 * @property unconditionalEdgeAssertions A list of assertions ensuring unconditional connections between nodes.
 * @property reachabilityAssertions A list of assertions verifying that one node in the graph is reachable from another node.
 * @property subgraphAssertions A mutable list of assertions related to specific subgraphs within the graph structure.
 */
@TestOnly
public data class GraphAssertions(
    val name: String,
    val start: NodeReference.Start<*>,
    val finish: NodeReference.Finish<*>,
    val nodes: Map<String, NodeReference<*, *>>,
    val nodeOutputAssertions: List<NodeOutputAssertion<*, *>>,
    val edgeAssertions: List<EdgeAssertion<*, *>>,
    val unconditionalEdgeAssertions: List<UnconditionalEdgeAssertion>,
    val reachabilityAssertions: List<ReachabilityAssertion>,
    val subgraphAssertions: MutableList<SubGraphAssertions>
)

/**
 * Represents an assertion for testing the output of a specific node within an AI agent's subgraph.
 *
 * This class is intended to be used for verifying that a node, when executed in a specific
 * agent context with a given input, produces the expected output.
 *
 * @param Input The type of the input data that the node processes.
 * @param Output The type of the output data produced by the node.
 * @property node A reference to the node whose behavior is being tested.
 * @property context The testing or mock context in which the node executes.
 * Provides the necessary environment and configurations for the node's execution.
 * @property input The input data passed to the node during execution.
 * @property expectedOutput The expected output data to be verified against the actual output from the node.
 */
@TestOnly
public data class NodeOutputAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: DummyAgentContext,
    val input: Input,
    val expectedOutput: Output
)

/**
 * Represents the assertion of an edge in a graph structure related to AI agent operations.
 *
 * @param Input The type of the input parameter for the node.
 * @param Output The type of the output parameter for the node.
 * @property node The reference to the current node involved in this edge assertion.
 * @property context The context associated with the AI agent during the assertion.
 * @property output The output value produced or expected by the edge during assertion.
 * @property expectedNode The reference to the expected node where the edge should lead.
 */
@TestOnly
public data class EdgeAssertion<Input, Output>(
    val node: NodeReference<Input, Output>,
    val context: AIAgentContextBase,
    val output: Output,
    val expectedNode: NodeReference<*, *>
)

/**
 * Represents an assertion for an unconditional edge in a graph structure.
 *
 * This data class is used to verify the existence of a direct connection from the `node` to the
 * `expectedNode` in a graph during tests. Such connections are evaluated without considering
 * any specific conditions or outputs, ensuring that the edge is universally established.
 *
 * @property node The source node of the edge to be asserted.
 * @property expectedNode The target node that the source node is expected to connect to.
 */
@TestOnly
public data class UnconditionalEdgeAssertion(
    val node: NodeReference<*, *>,
    val expectedNode: NodeReference<*, *>
)

/**
 * Represents an assertion for testing the reachability between two specific nodes in a graph.
 *
 * This class is primarily used in graph-based testing contexts to verify that a directed path
 * exists from a starting node (`from`) to a target node (`to`) within an agent graph.
 *
 * @property from The starting node reference for the reachability assertion.
 * @property to The target node reference for the reachability assertion.
 *
 * This assertion helps validate correctness and connectivity properties of constructed graphs.
 */
@TestOnly
public data class ReachabilityAssertion(val from: NodeReference<*, *>, val to: NodeReference<*, *>)

/**
 * Encapsulates assertions for a subgraph within a graph testing framework.
 *
 * This class links a specific subgraph, represented by a `SubgraphNode`, with its corresponding
 * graph-wide assertions. It is intended to facilitate hierarchical testing and validation of
 * graph components in isolation or as part of a larger graph structure.
 *
 * @property subgraphRef Reference to the subgraph being asserted, represented as a `SubgraphNode`.
 * @property graphAssertions Graph-wide assertions relevant to the subgraph.
 */
@TestOnly
public data class SubGraphAssertions(
    val subgraphRef: NodeReference.SubgraphNode<*, *>,
    val graphAssertions: GraphAssertions
)

/**
 * Represents the result of an assertion used within the testing framework.
 *
 * This sealed interface allows defining various outcomes of assertions, such as
 * inequality or false conditions.
 */
@TestOnly
public sealed interface AssertionResult {
    /**
     * Represents the result of a failed equality assertion.
     *
     * This class is used to specify details about the expected and actual values
     * when an equality assertion fails. The failure may include an optional message
     * providing additional context.
     *
     * @param expected The expected value in the comparison.
     * @param actual The actual value found during the comparison.
     * @param message A custom message explaining why the assertion failed.
     */
    public class NotEqual(public val expected: Any?, public val actual: Any?, public val message: String) :
        AssertionResult

    /**
     * Represents an assertion result indicating a failed assertion due to a false condition.
     *
     * This class is used when a boolean condition evaluates to false during an assertion.
     * It encapsulates an error message describing the failure.
     *
     * @property message The message providing details about the failed assertion.
     */
    public class False(public val message: String) : AssertionResult
}

/**
 * Provides functionality for testing graph-related stages in an AI agent pipeline.
 *
 * This feature allows you to configure and validate the relationships between nodes,
 * their outputs, and the overall graph structure within different stages of an agent.
 * It can validate:
 * - Stage order
 * - Node existence and reachability
 * - Node input/output behavior
 * - Edge connections between nodes
 *
 * The Testing feature is designed to be used with the [testGraph] function, which provides
 * a clean API for defining and executing graph tests.
 *
 * Example usage:
 * ```kotlin
 * AIAgent(
 *     // constructor arguments
 * ) {
 *     testGraph {
 *         // Assert the order of stages
 *         assertStagesOrder("first", "second")
 *
 *         // Test the first stage
 *         stage("first") {
 *             val start = startNode()
 *             val finish = finishNode()
 *
 *             // Assert nodes by name
 *             val askLLM = assertNodeByName<String, Message.Response>("callLLM")
 *             val callTool = assertNodeByName<ToolCall.Signature, ToolCall.Result>("executeTool")
 *
 *             // Assert node reachability
 *             assertReachable(start, askLLM)
 *             assertReachable(askLLM, callTool)
 *
 *             // Test node behavior
 *             assertNodes {
 *                 askLLM withInput "Hello" outputs Message.Assistant("Hello!")
 *             }
 *
 *             // Test edge connections
 *             assertEdges {
 *                 askLLM withOutput Message.Assistant("Hello!") goesTo giveFeedback
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @see testGraph
 * @see Testing.Config
 */
@TestOnly
public class Testing {
    /**
     * A mutable list storing `GraphAssertions` objects, which define validation
     * criteria for the graphs.
     *
     */
    private val graphAssertions = mutableListOf<GraphAssertions>()

    /**
     * Represents a configuration class responsible for managing assertion handlers and stage configurations.
     * It includes methods for registering custom assertion handling, managing stages and their order,
     * and defining stage-specific assertions.
     */
    public class Config : FeatureConfig() {
        /**
         * A clock instance used for managing timestamps within the configuration,
         * primarily for mock message timestamping purposes.
         *
         * This enables test scenarios that require precise control over time
         * by allowing the use of custom clock instances, such as mock or fixed clocks.
         */
        public var clock: Clock = Clock.System

        /**
         * Defines the tokenizer to be used for estimating token counts in text strings.
         *
         * Tokenizers are critical for features or applications requiring token-level control
         * or analysis, such as evaluating text input size relative to limits or optimizing
         * messages for LLM prompts. By default, this is set to `null`, which disables
         * token counting, but it can be replaced with a custom implementation of the `Tokenizer`
         * interface.
         *
         * Assigning a different tokenizer allows for customizable token estimation strategies
         * with varying levels of accuracy and performance depending on the use case.
         */
        public var tokenizer: Tokenizer? = null

        /**
         * A configuration flag that determines whether graph-related testing features are enabled.
         *
         * When set to `true`, additional testing mechanisms related to graph validation,
         * assertions, or structure evaluation may be activated within the system.
         * Default value is `false`, which disables graph testing functionalities.
         */
        public var enableGraphTesting: Boolean = false

        /**
         * A nullable variable that defines the handler invoked when an assertion result is generated.
         * It is a higher-order function that accepts an `AssertionResult` as its parameter.
         * This handler is used internally in assertion methods to process and handle assertion outcomes such as
         * mismatches between expected and actual values or boolean evaluation failures.
         */
        private var assertionHandler: ((AssertionResult) -> Unit)? = null

        /**
         * A mutable list of `StrategyAssertions` objects used to collect and manage graph-related assertions.
         *
         * Each element in the list represents a specific (sub)graph with its associated assertions, such as node outputs,
         * edge connections, and reachability between nodes.
         */
        private var assertions: GraphAssertions? = null

        /**
         * Retrieves the list of graph assertions defined in the configuration.
         *
         * @return A list of `GraphAssertions` representing the assertions to be applied for graph testing.
         */
        internal fun getAssertions(): GraphAssertions = assertions!!

        /**
         * Asserts that two values are equal. If the values are not equal, the specified message will be passed
         * to the assertion handler with details about the expected and actual values.
         *
         * @param expected The expected value to compare against.
         * @param actual The actual value to be compared.
         * @param message A custom message to include when the assertion fails.
         */
        internal fun assertEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                assertionHandler?.invoke(AssertionResult.NotEqual(expected, actual, message))
            }
        }

        /**
         * Asserts the truthfulness of the provided boolean value.
         * If the assertion fails (i.e., the value is false), a custom handler is invoked
         * with an `AssertionResult.False` containing the provided message.
         *
         * @param value the boolean value to evaluate. If false, the assertion fails.
         * @param message the message to include in the assertion result if the value is false.
         */
        internal fun assert(value: Boolean, message: String) {
            if (!value) {
                assertionHandler?.invoke(AssertionResult.False(message))
            }
        }

        /**
         * Sets a custom handler for processing assertion results.
         *
         * @param block A lambda which takes an `AssertionResult` as input and processes it.
         *              This allows customization of how assertion results are handled,
         *              such as logging or throwing custom exceptions.
         */
        public fun handleAssertion(block: (AssertionResult) -> Unit) {
            assertionHandler = block
        }

        /**
         * Configures and adds a verification strategy using the provided name and assertions.
         *
         * @param name The name of the strategy to be verified.
         * @param buildAssertions A lambda defining the assertions to be built for the strategy.
         */
        public fun verifyStrategy(name: String, buildAssertions: SubgraphAssertionsBuilder<String, String>.() -> Unit) {
            assertions =
                SubgraphAssertionsBuilder(NodeReference.Strategy(name), clock, tokenizer).apply(buildAssertions).build()
        }

        /**
         * Builder class for constructing subgraph-level assertions.
         * This includes setup for nodes, edges, reachability assertions, and context-related mock setups.
         *
         * @param subgraphRef: A [NodeReference.SubgraphNode] reference to a subgraph of the agent's graph strategy
         * @param clock: A clock that is used for mock message timestamps
         * @param tokenizer: Tokenizer that will be used to estimate token counts in mock messages
         */
        public class SubgraphAssertionsBuilder<Input, Output>(
            private val subgraphRef: NodeReference.SubgraphNode<Input, Output>,
            internal val clock: Clock,
            internal val tokenizer: Tokenizer?,
        ) {

            private val start: NodeReference.Start<Input> = NodeReference.Start<Input>()

            private val finish: NodeReference.Finish<Output> = NodeReference.Finish<Output>()


            /**
             * Stores a mapping of node names to their corresponding references.
             *
             * This property serves as the central repository of named nodes within the
             * StageAssertionsBuilder. Nodes are registered within this map when they are
             * asserted by name using functions like `assertNodeByName`. The keys in the map
             * represent the names of the nodes, while the values represent their references.
             *
             * The `nodes` map is later used during the `build()` process to construct
             * `StageAssertions`, ensuring all nodes are properly accounted for and linked
             * according to their usage in the assertions.
             */
            private val nodes = mutableMapOf<String, NodeReference<*, *>>()

            /**
             * A mutable list that collects NodeOutputAssertion instances. These assertions are used to validate
             * the outputs of specific nodes within a stage of the system's execution.
             *
             * Each NodeOutputAssertion in this list represents an expectation on the output behavior of a
             * particular node when provided with a certain input in a predefined context.
             *
             * This property is populated indirectly through the `assertNodes` function within the
             * `StageAssertionsBuilder` context, allowing users to define node-specific assertions
             * and add them to this collection.
             *
             * The collected assertions are later utilized during the construction of the
             * `StageAssertions` object to verify node output behavior.
             */
            private val nodeOutputs = mutableListOf<NodeOutputAssertion<*, *>>()

            /**
             * A mutable list storing all edge-related assertions for a stage.
             *
             * Each edge assertion represents an expected edge between two nodes in the
             * stage, including the originating node, its output, the target node, and the
             * execution context in which the assertion was made.
             *
             * This property is utilized during the edge validation process to ensure the
             * stage conforms to its designed behavior regarding node-to-node transitions.
             * The assertions are collected through the `EdgeAssertionsBuilder` and
             * integrated into the final `StageAssertions` object for the stage.
             */
            private val edgeAssertions = mutableListOf<EdgeAssertion<*, *>>()

            /**
             * Stores a collection of assertions that represent unconditional and direct relationships
             * between nodes in the graph. Each assertion describes an expected edge connection
             * without conditions or additional constraints.
             *
             * This property is typically populated during the process of asserting edge connections
             * within a graph, specifically using the `assertEdges` method or similar graph
             * assertion configurations.
             *
             * It is used internally to track and validate edge connections as part of the
             * graph testing framework to ensure the graph structure behaves as expected.
             */
            private val unconditionalEdgeAssertions = mutableListOf<UnconditionalEdgeAssertion>()

            /**
             * A mutable list of reachability assertions that define expected direct connections between nodes
             * in a stage. Each assertion represents a relationship where one node is reachable from another.
             *
             * This field is used to accumulate `ReachabilityAssertion` objects which are added via the
             * `assertReachable` method in the `StageAssertionsBuilder` class. These assertions are validated
             * when the stage's configuration is built using the `build` method.
             */
            private val reachabilityAssertions = mutableListOf<ReachabilityAssertion>()

            private val subgraphAssertions = mutableListOf<SubGraphAssertions>()

            /**
             * Provides a mock builder for the local agent stage context used within test environments.
             * This mock can be leveraged to construct or replicate contexts for validating various stage behaviors
             * including node outputs, edge assertions, and reachability assertions.
             * It acts as a centralized resource for contextual test data and stage-related configurations.
             */
            private val context = AIAgentContextMockBuilder()

            /**
             * Retrieves the starting node reference of the stage.
             *
             * @return The starting node reference of type NodeReference.Start, representing the entry point of the stage.
             */
            public fun startNode(): NodeReference.Start<Input> {
                return start
            }

            /**
             * Retrieves a reference to the finish node of the current stage in the graph.
             *
             * @return a [NodeReference.Finish] representing the terminal node of the stage.
             */
            public fun finishNode(): NodeReference.Finish<Output> {
                return finish
            }

            /**
             * Asserts the existence of a node by its name within the stage structure and returns a reference to it.
             * If the node with the given name has not been asserted previously, it creates a new `NamedNode` reference
             * and records it in the `nodes` map for tracking.
             *
             * @param name the name of the node to assert or retrieve.
             * @return a `NodeReference` for the node identified by the given name.
             */
            public fun <I, O> assertNodeByName(name: String): NodeReference.NamedNode<I, O> {
                val nodeRef = NodeReference.NamedNode<I, O>(name)
                nodes[name] = nodeRef
                return nodeRef
            }

            /**
             * Asserts the existence of a subgraph by its name*/
            public fun <I, O> assertSubgraphByName(
                name: String
            ): NodeReference.SubgraphNode<I, O> {
                val nodeRef = NodeReference.SubgraphNode<I, O>(name)
                nodes[name] = nodeRef
                return nodeRef
            }

            /**
             * Verifies a given subgraph by applying a set of assertions to it using a provided configuration block.
             *
             * @param subgraph the subgraph to verify, represented as a [NodeReference.SubgraphNode] of input and output types [I] and [O].
             * @param checkSubgraph a lambda receiver operating on a [SubgraphAssertionsBuilder] that defines the assertions to be applied to the subgraph.
             */
            public fun <I, O> verifySubgraph(
                subgraph: NodeReference.SubgraphNode<I, O>,
                checkSubgraph: SubgraphAssertionsBuilder<I, O>.() -> Unit = {}
            ) {
                val assertions = SubgraphAssertionsBuilder(subgraph, clock, tokenizer).apply(checkSubgraph).build()
                subgraphAssertions.add(SubGraphAssertions(subgraph, assertions))
            }

            /**
             * Asserts that there is a reachable path between two specified nodes within a stage.
             *
             * @param from The starting node reference from where the reachability is checked.
             * @param to The target node reference to which the reachability is checked.
             */
            public fun assertReachable(from: NodeReference<*, *>, to: NodeReference<*, *>) {
                reachabilityAssertions.add(ReachabilityAssertion(from, to))
            }

            /**
             * Asserts the state of nodes in the stage using a provided block to define the desired assertions.
             * The block operates on a `NodeOutputAssertionsBuilder` to specify expectations for node inputs and outputs.
             *
             * @param block A lambda receiver operating on a `NodeOutputAssertionsBuilder` that defines the assertions
             *              to be applied to the nodes in the stage.
             */
            public fun assertNodes(block: NodeOutputAssertionsBuilder.() -> Unit) {
                val builder = NodeOutputAssertionsBuilder(this)
                builder.block()
                nodeOutputs.addAll(builder.assertions)
            }

            /**
             * Asserts the edges in the context of a graph by applying a set of edge assertions built using the provided block.
             *
             * @param block A lambda function that operates on an instance of `EdgeAssertionsBuilder` to define specific edge assertions.
             */
            public fun assertEdges(block: EdgeAssertionsBuilder.() -> Unit) {
                val builder = EdgeAssertionsBuilder(this)
                builder.block()
                edgeAssertions.addAll(builder.assertions)
                unconditionalEdgeAssertions.addAll(builder.unconditionalEdgeAssertions)
            }

            /**
             * Builds and returns a `StageAssertions` object based on the current state of the `StageAssertionsBuilder`.
             *
             * @return A `StageAssertions` instance containing the name, start and finish node references,
             *         map of nodes, node output assertions, edge assertions, and reachability assertions.
             */
            internal fun build(): GraphAssertions {
                return GraphAssertions(
                    subgraphRef.name,
                    start,
                    finish,
                    nodes,
                    nodeOutputs,
                    edgeAssertions,
                    unconditionalEdgeAssertions,
                    reachabilityAssertions,
                    subgraphAssertions
                )
            }

            /**
             * A builder class for constructing and managing assertions related to the outputs of nodes within a stage.
             * This class provides functionality to define and evaluate assertions for node outputs in a local agent's stage.
             *
             * @property stageBuilder A reference to the parent StageAssertionsBuilder, which serves as the context for assertions.
             * @property context A mock builder for the local agent stage context, used to manage and copy state during the assertion process.
             */
            public class NodeOutputAssertionsBuilder(
                private val stageBuilder: SubgraphAssertionsBuilder<*, *>,
                private val context: AIAgentContextMockBuilder = stageBuilder.context.copy()
            ) : AIAgentContextMockBuilderBase by context {

                /**
                 * Creates and returns a new copy of the NodeOutputAssertionsBuilder instance.
                 *
                 * @return a new NodeOutputAssertionsBuilder that contains a copy of the current stageBuilder
                 * and a copied context.
                 */
                override fun copy(): NodeOutputAssertionsBuilder =
                    NodeOutputAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * A mutable list that stores assertions representing the expected behavior and output of nodes
                 * in the context of a specific staging environment for testing purposes.
                 *
                 * Each assertion is an instance of [NodeOutputAssertion], which encapsulates information
                 * such as the node reference, the input provided, the expected output, and the context
                 * in which the node operates.
                 *
                 * These assertions are used to verify the correctness of node operations within the
                 * local agent stage context during testing.
                 */
                public val assertions: MutableList<NodeOutputAssertion<*, *>> = mutableListOf()

                /**
                 * Executes the specified block of code within a duplicate context of the current `NodeOutputAssertionsBuilder`.
                 *
                 * @param block The block of code to be executed within the duplicated context of `NodeOutputAssertionsBuilder`.
                 */
                public fun withContext(block: NodeOutputAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the provided input with the current node reference, creating a pair that links the node
                 * to its corresponding input.
                 *
                 * @param input The input value to associate with the node reference.
                 * @return A `NodeOutputPair` containing the node reference and the provided input.
                 */
                public infix fun <I, O> NodeReference<I, O>.withInput(input: I): NodeOutputPair<I, O> {
                    return NodeOutputPair(this, input)
                }

                /**
                 * Represents a pairing of a specific node reference and its corresponding input.
                 * This is used to define the expected output for a given input within the context of a specific node.
                 *
                 * @param I The type of the input for the node.
                 * @param O The type of the output for the node.
                 * @property node The reference to the specific node.
                 * @property input The input associated with the node.
                 */
                public inner class NodeOutputPair<I, O>(public val node: NodeReference<I, O>, public val input: I) {
                    /**
                     * Asserts that the output of the current node given the specified input matches the expected output.
                     *
                     * @param output The expected output to validate against the current node's actual output.
                     */
                    public infix fun outputs(output: O) {
                        assertions.add(NodeOutputAssertion(node, context.build(), input, output))
                    }
                }
            }

            /**
             * A builder class used to facilitate the creation and management of edge assertions in a stage context.
             * Delegates functionality to a local agent stage context mock builder for shared behaviors.
             *
             * @property stageBuilder The parent builder for the stage, used to initialize context and other related components.
             * @property context A local agent stage context mock builder, initialized as a copy of the stage builder's context.
             */
            public class EdgeAssertionsBuilder(
                private val stageBuilder: SubgraphAssertionsBuilder<*, *>,
                private val context: AIAgentContextMockBuilder = stageBuilder.context.copy()
            ) : AIAgentContextMockBuilderBase by context {

                /**
                 * A mutable list that holds all the defined `EdgeAssertion` instances for the current context.
                 *
                 * `EdgeAssertion` represents the relationship between nodes in a graph-like structure, detailing
                 * the output of a source node and its expected connection to a target node. These assertions are
                 * used to validate the behavior and flow of nodes within an agent's stage context.
                 *
                 * This list is populated during the execution of the `EdgeAssertionsBuilder` block in methods
                 * that build or define edge assertions. Each assertion is added via the respective fluent APIs
                 * provided within the builder.
                 */
                public val assertions: MutableList<EdgeAssertion<*, *>> = mutableListOf()

                /**
                 * A collection that stores assertions ensuring an unconditional connection
                 * between nodes in a graph testing context. Each assertion represents
                 * a defined relationship where a node always leads to a specified target node.
                 *
                 * This list is populated by adding instances of [UnconditionalEdgeAssertion] through relevant methods,
                 * such as when defining relationships or validating graph behavior.
                 */
                public val unconditionalEdgeAssertions: MutableList<UnconditionalEdgeAssertion> =
                    mutableListOf<UnconditionalEdgeAssertion>()

                /**
                 * Creates a deep copy of the current EdgeAssertionsBuilder instance, duplicating its state and context.
                 *
                 * @return A new EdgeAssertionsBuilder instance with the same stageBuilder and a copied context.
                 */
                override fun copy(): EdgeAssertionsBuilder = EdgeAssertionsBuilder(stageBuilder, context.copy())

                /**
                 * Executes a given block of logic within the context of a copied instance of the current `EdgeAssertionsBuilder`.
                 *
                 * @param block The block of code to execute within the context of the copied `EdgeAssertionsBuilder` instance.
                 */
                public fun withContext(block: EdgeAssertionsBuilder.() -> Unit) {
                    with(copy(), block)
                }

                /**
                 * Associates the given output with the current node reference, creating a pair that represents
                 * the node and its corresponding output.
                 *
                 * @param output the output value to associate with the current node reference
                 * @return an instance of EdgeOutputPair containing the current node reference and the associated output
                 */
                public infix fun <I, O> NodeReference<I, O>.withOutput(output: O): EdgeOutputPair<I, O> {
                    return EdgeOutputPair(this, output)
                }

                /**
                 * Creates an assertion to verify that the current node always leads to the given target node.
                 *
                 * @param targetNode The target node that the current node output is expected to connect to.
                 */
                public infix fun NodeReference<*, *>.alwaysGoesTo(targetNode: NodeReference<*, *>) {
                    unconditionalEdgeAssertions.add(UnconditionalEdgeAssertion(this, targetNode))
                }

                /**
                 * Represents a pair consisting of a node and its corresponding output within an edge assertion context.
                 * This is used to define expected edge behavior in a graph of nodes.
                 *
                 * @param I the type of the input expected by the node.
                 * @param O the type of the output produced by the node.
                 * @property node the reference to the node associated with this edge output.
                 * @property output the output value associated with the node.
                 */
                public inner class EdgeOutputPair<I, O>(public val node: NodeReference<I, O>, public val output: O) {
                    /**
                     * Creates an assertion to verify that a specific output from the current node leads to the given target node.
                     *
                     * @param targetNode The target node that the current node output is expected to connect to.
                     */
                    public infix fun goesTo(targetNode: NodeReference<*, *>) {
                        assertions.add(EdgeAssertion(node, context.build(), output, targetNode))
                    }
                }
            }
        }
    }

    /**
     * Companion object that defines the `Testing` feature as a `AIAgentFeature`.
     * This feature provides testing capabilities for validating graph-based stages, nodes,
     * reachability, outputs, and edges within an AI agent pipeline.
     */
    @TestOnly
    public companion object Feature : AIAgentFeature<Config, Testing> {
        /**
         * A storage key uniquely identifying the `Testing` feature within the local agent's storage.
         * The key is generated using the `createStorageKey` function and associates the
         * `Testing` feature type with its specific storage context.
         */
        override val key: AIAgentStorageKey<Testing> = createStorageKey("graph-testing-feature")

        /**
         * Creates the initial configuration for the graph testing feature.
         *
         * @return an instance of [Config] containing the initial setup for assertions and stage configuration.
         */
        override fun createInitialConfig(): Config = Config()

        /**
         * Installs the `Testing` feature into the specified `AIAgentPipeline` with the provided configuration.
         * The feature primarily validates stages, nodes, and connectivity of the AI agent pipeline.
         *
         * @param config The `Config` object containing setup and assertions for testing the pipeline.
         * @param pipeline The `AIAgentPipeline` instance to install the feature into.
         */
        override fun install(
            config: Config,
            pipeline: AIAgentPipeline
        ) {
            val feature = Testing()
            val interceptContext = InterceptContext(this, feature)
            pipeline.interceptEnvironmentCreated(interceptContext) { agentEnvironment ->
                MockEnvironment(agent.toolRegistry, agent.promptExecutor, agentEnvironment)
            }

            if (config.enableGraphTesting) {
                feature.graphAssertions.add(config.getAssertions())

                pipeline.interceptBeforeAgentStarted(interceptContext) {
                    readStrategy { strategyGraph ->
                        val strategyAssertions = feature.graphAssertions.find { it.name == strategyGraph.name }
                        config.assert(
                            strategyAssertions != null,
                            "Assertions for strategyGraph with name `${strategyGraph.name}` not found in configuration."
                        )
                        strategyAssertions!!
                        verifyGraph(agent, strategyAssertions, strategyGraph, pipeline, config)
                    }
                }
            }
        }

        private suspend fun verifyGraph(
            agent: AIAgent,
            graphAssertions: GraphAssertions,
            graph: AIAgentSubgraph<*, *>,
            pipeline: AIAgentPipeline,
            config: Config
        ) {
            // Verify nodes exist
            for ((nodeName, nodeRef) in graphAssertions.nodes) {
                val node = nodeRef.resolve(graph)
                assertNotNull(
                    node,
                    "Node with name '$nodeName' not found in graph '${graph.name}'"
                )
            }

            // Verify reachability
            for (assertion in graphAssertions.reachabilityAssertions) {
                assertReachable(
                    assertion.from.resolve(graph),
                    assertion.to.resolve(graph),
                    "Node ${assertion.to.resolve(graph).name} is not reachable from ${
                        assertion.from.resolve(
                            graph
                        ).name
                    } in graph '${graph.name}'"
                )
            }

            // Verify node outputs using DFS
            for (assertion in graphAssertions.nodeOutputAssertions) {
                val fromNode = assertion.node.resolve(graph)

                val environment = if (assertion.context.isEnvironmentDefined)
                    assertion.context.environment
                else
                    MockEnvironment(agent.toolRegistry, agent.promptExecutor)

                val llm = if (assertion.context.isLLMDefined) {
                    assertion.context.llm
                } else {
                    AIAgentLLMContext(
                        tools = agent.toolRegistry.tools.map { it.descriptor },
                        prompt = agent.agentConfig.prompt,
                        model = agent.agentConfig.model,
                        promptExecutor = PromptExecutorProxy(
                            sessionId = assertion.context.sessionId.toString(),
                            executor = agent.promptExecutor,
                            pipeline = pipeline,
                        ),
                        environment = environment,
                        config = agent.agentConfig,
                        clock = agent.clock
                    )
                }

                @OptIn(InternalAgentsApi::class)
                config.assertEquals(
                    assertion.expectedOutput,
                    fromNode.executeUnsafe(
                        assertion.context.copy(llm = llm, environment = environment),
                        assertion.input
                    ),
                    "Unexpected output for node ${fromNode.name} with input ${assertion.input} " +
                            "in graph '${graph.name}'"
                )
            }

            @OptIn(InternalAgentsApi::class)
            // Verify edges using DFS
            for (assertion in graphAssertions.edgeAssertions) {
                val fromNode = assertion.node.resolve(graph)
                val toNode = assertion.expectedNode.resolve(graph)

                val resolvedEdge = fromNode.resolveEdgeUnsafe(assertion.context, assertion.output)
                assertNotNull(
                    resolvedEdge,
                    "Expected to have at least one matching edge from node ${fromNode.name} with output ${assertion.output} "
                )

                config.assertEquals(
                    toNode,
                    resolvedEdge!!.edge.toNode,
                    "Expected `${fromNode.name}` with output ${assertion.output} to go to `${toNode.name}`, " +
                            "but it goes to ${resolvedEdge.edge.toNode.name} instead"
                )
            }

            @OptIn(InternalAgentsApi::class)
            // Verify edges using DFS
            for (assertion in graphAssertions.unconditionalEdgeAssertions) {
                val fromNode = assertion.node.resolve(graph)
                val toNode = assertion.expectedNode.resolve(graph)

                config.assertEquals(
                    1, fromNode.edges.size,
                    "Expected node ${fromNode.name} to have exactly one edge, " +
                            "but it has ${fromNode.edges.size} edges instead"
                )

                val actualToNode = fromNode.edges.single().toNode

                config.assertEquals(
                    toNode, actualToNode,
                    "Expected that from node ${fromNode.name} the only edge is going to ${toNode}, " +
                            "but it goes to ${actualToNode} instead"
                )
            }

            for (assertion in graphAssertions.subgraphAssertions) {
                verifyGraph(agent, assertion.graphAssertions, assertion.subgraphRef.resolve(graph), pipeline, config)
            }
        }

        /**
         * Verifies whether there is a path from one node to another within a graph. If the target node
         * is not reachable from the starting node, an assertion error is thrown with the provided message.
         *
         * @param from The starting node in the graph from which to check reachability.
         * @param to The target node to verify reachability to.
         * @param message The error message to include in the assertion error if the target node is not reachable.
         */
        private fun assertReachable(from: AIAgentNodeBase<*, *>, to: AIAgentNodeBase<*, *>, message: String) {
            val visited = mutableSetOf<String>()

            fun dfs(node: AIAgentNodeBase<*, *>): Boolean {
                if (node == to) return true
                if (visited.contains(node.name)) return false

                visited.add(node.name)

                for (edge in node.edges) {
                    if (dfs(edge.toNode)) return true
                }

                return false
            }

            if (!dfs(from)) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the provided value is not null. If the value is null, an AssertionError is thrown with the specified message.
         *
         * @param value The value to check for nullity.
         * @param message The error message to include in the exception if the value is null.
         */
        private fun assertNotNull(value: Any?, message: String) {
            if (value == null) {
                throw AssertionError(message)
            }
        }

        /**
         * Compares two lists and throws an AssertionError if they are not equal, with a specified error message.
         *
         * @param expected The expected list of elements.
         * @param actual The actual list of elements to compare against the expected list.
         * @param message The message to include in the assertion error if the lists are not equal.
         */
        private fun assertListEquals(expected: List<*>, actual: List<*>, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }

        /**
         * Asserts that the given two values are equal. If they are not equal, it throws an AssertionError
         * with the provided message.
         *
         * @param expected the expected value to compare
         * @param actual the actual value to compare against the expected value
         * @param message the assertion failure message to include in the exception if the values are not equal
         */
        private fun assertValueEquals(expected: Any?, actual: Any?, message: String) {
            if (expected != actual) {
                throw AssertionError(message)
            }
        }
    }
}

/**
 * Creates a `Message.Tool.Call` instance for the given tool and its arguments.
 *
 * This utility function simplifies the creation of tool call messages for testing purposes.
 * It automatically handles the encoding of arguments into the appropriate string format.
 *
 * @param tool The tool to be represented in the call message. The `Tool` instance contains metadata
 *             such as the tool's name and utility methods for encoding/decoding arguments.
 * @param args The arguments to be passed to the tool. Must match the type `Args` defined by the tool.
 * @return A `Message.Tool.Call` object representing a call to the specified tool with the encoded arguments.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool call message for testing
 * val message = toolCallMessage(CreateTool, CreateTool.Args("solve"))
 *
 * // Use in node output assertions
 * assertNodes {
 *     askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))
 * }
 * ```
 */
public fun <Args : ToolArgs> Testing.Config.SubgraphAssertionsBuilder<*, *>.toolCallMessage(
    tool: Tool<Args, *>,
    args: Args
): Message.Tool.Call {
    val toolContent = tool.encodeArgsToString(args)
    val tokenCount = tokenizer?.countTokens(toolContent)

    return Message.Tool.Call(
        id = null,
        tool = tool.name,
        content = toolContent,
        metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenCount)
    )
}

/**
 * Creates an assistant message with the provided text and finish reason.
 *
 * @param text The content of the assistant message.
 * @param finishReason The reason indicating why the message was concluded. Defaults to null.
 * @return A new instance of Message.Assistant containing the provided content, finish reason, and associated metadata.
 */
public fun Testing.Config.SubgraphAssertionsBuilder<*, *>.assistantMessage(
    text: String,
    finishReason: String? = null
): Message.Assistant {
    val tokenCount = tokenizer?.countTokens(text)

    return Message.Assistant(
        content = text,
        finishReason = finishReason,
        metaInfo = ResponseMetaInfo.create(clock, outputTokensCount = tokenCount)
    )
}

/**
 * Converts a tool and its corresponding result into a `ReceivedToolResult` object.
 *
 * This utility function simplifies the creation of tool results for testing purposes.
 * It automatically handles the encoding of the result into the appropriate string format.
 *
 * @param tool The tool whose result is being processed. The tool provides context for the result.
 * @param result The result produced by the tool, which will be encoded into a string representation.
 * @return A `ReceivedToolResult` instance containing the tool's name and the encoded string representation of the result.
 *
 * Example usage:
 * ```kotlin
 * // Create a tool result for testing
 * val result = toolResult(AnalyzeTool, AnalyzeTool.Result("Detailed analysis", 0.95))
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(AnalyzeTool, AnalyzeTool.Args("analyze")) outputs result
 * }
 * ```
 */
public fun <Result : ToolResult> toolResult(tool: Tool<*, Result>, result: Result): ReceivedToolResult =
    ReceivedToolResult(null, tool.name, tool.encodeResultToString(result), result)

/**
 * Constructs a `ReceivedToolResult` object using the provided tool and result string.
 *
 * This is a convenience function for simple tools that return text results.
 * It wraps the string result in a `ToolResult.Text` object.
 *
 * @param tool The tool for which the result is being created, of type `SimpleTool`.
 * @param result The result content generated by the tool execution as a string.
 * @return An instance of `ReceivedToolResult` containing the tool's name and the result string.
 *
 * Example usage:
 * ```kotlin
 * // Create a simple text tool result for testing
 * val result = toolResult(SolveTool, "solved")
 *
 * // Use in node output assertions
 * assertNodes {
 *     callTool withInput toolCallSignature(SolveTool, SolveTool.Args("solve")) outputs result
 * }
 * ```
 */
public fun toolResult(tool: SimpleTool<*>, result: String): ReceivedToolResult =
    toolResult(tool, ToolResult.Text(result))

/**
 * Enables and configures the Testing feature for a Kotlin AI Agent instance.
 *
 * This function installs the Testing feature with the specified configuration.
 * It's typically used within the agent constructor block to enable testing capabilities.
 *
 * @param config A lambda function to configure the Testing feature. The default is an empty configuration.
 *
 * Example usage:
 * ```kotlin
 * // Create an agent with testing enabled
 * AIAgent(
 *     promptExecutor = mockLLMApi,
 *     toolRegistry = toolRegistry,
 *     strategy = strategy,
 *     eventHandler = eventHandler,
 *     agentConfig = agentConfig,
 * ) {
 *     // Enable testing with custom configuration
 *     withTesting {
 *         enableGraphTesting = true
 *         handleAssertion { assertionResult ->
 *             // Custom assertion handling
 *         }
 *     }
 * }
 * ```
 *
 * @see Testing
 * @see Testing.Config
 */
public fun FeatureContext.withTesting(config: Testing.Config.() -> Unit = {}) {
    install(Testing) {
        config()
    }
}
