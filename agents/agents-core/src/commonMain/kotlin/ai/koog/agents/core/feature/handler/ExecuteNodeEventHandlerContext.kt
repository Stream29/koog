package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase

/**
 * Represents the context for handling node-specific events within the framework.
 */
public interface NodeEventHandlerContext : EventHandlerContext

/**
 * Represents the context for handling a before node execution event.
 *
 * @property context The stage context in which the node is being executed.
 * @property node The node that is about to be executed.
 * @property input The input data for the node execution.
 */
public data class BeforeNodeHandlerContext(
    val context: AIAgentContextBase,
    val node: AIAgentNodeBase<*, *>,
    val input: Any?
) : NodeEventHandlerContext

/**
 * Represents the context for handling an after node execution event.
 *
 * @property context The stage context in which the node was executed.
 * @property node The node that was executed.
 * @property input The input data that was provided to the node.
 * @property output The output data produced by the node execution.
 */
public data class AfterNodeHandlerContext(
    val context: AIAgentContextBase,
    val node: AIAgentNodeBase<*, *>,
    val input: Any?,
    val output: Any?
) : NodeEventHandlerContext
