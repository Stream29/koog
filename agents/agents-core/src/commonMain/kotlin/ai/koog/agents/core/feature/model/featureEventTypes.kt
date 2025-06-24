package ai.koog.agents.core.feature.model

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents a sealed class for defining feature-related events in the system.
 *
 * This class serves as a foundational type from which specific feature events are derived.
 * Its purpose is to encapsulate shared properties and functionality across all specialized
 * feature events. Each subclass details a specific type of event, such as agent lifecycle
 * updates or processing steps.
 *
 * This class implements the [FeatureEvent] interface, ensuring compatibility with the
 * system's feature event handling mechanisms.
 *
 * @constructor Initializes a new instance of the `DefinedFeatureEvent` class.
 */
@Serializable
public sealed class DefinedFeatureEvent() : FeatureEvent {
    /**
     * The timestamp, represented as the number of milliseconds since the Unix epoch,
     * indicating when the feature event was created.
     *
     * This property is used to track the exact creation or occurrence time of the feature event.
     * It provides temporal context for ordering events, correlating feature messages, and
     * performing time-based analysis within the system.
     */
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds()
    /**
     * Specifies the type of the feature message for this event.
     *
     * This property is overridden to indicate that the message type of this
     * feature is categorized as an `Event`. The `Event` type is used to represent
     * occurrences or actions within the system, providing context to event-specific
     * feature messages.
     */
    override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
}

//region Agent

/**
 * Represents an event triggered when an AI agent starts executing a strategy.
 *
 * This event provides details about the agent's strategy, making it useful for
 * monitoring, debugging, and tracking the lifecycle of AI agents within the system.
 *
 * @property strategyName The name of the strategy that the AI agent has started executing.
 * @property eventId Unique identifier for this event, defaulting to the class name.
 */
@Serializable
public data class AIAgentStartedEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStartedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Event representing the completion of an AI Agent's execution.
 *
 * This event is emitted when an AI Agent finishes executing a strategy, providing
 * information about the strategy and its result. It can be used for logging, tracing,
 * or monitoring the outcomes of agent operations.
 *
 * @property strategyName The name of the executed strategy.
 * @property result The result of the strategy execution, or null if unavailable.
 * @property eventId The unique identifier for this event, defaulting to the name of the class.
 */
@Serializable
public data class AIAgentFinishedEvent(
    val agentId: String,
    val sessionId: String,
    val strategyName: String,
    val result: String?,
    override val eventId: String = AIAgentFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event triggered when an AI agent run encounters an error.
 *
 * This event is used to capture error information during the execution of an AI agent
 * strategy, including details of the strategy and the encountered error.
 *
 * @constructor Creates an instance of [AIAgentRunErrorEvent].
 * @property strategyName The name of the strategy being executed when the error occurred.
 * @property error The [AIAgentError] instance encapsulating details about the encountered error,
 * such as its message, stack trace, and cause.
 * @property eventId A unique identifier for this event type, defaulting to the class name.
 */
@Serializable
public data class AIAgentRunErrorEvent(
    val strategyName: String,
    val error: AIAgentError,
    override val eventId: String = AIAgentRunErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Agent

//region Strategy

/**
 * Represents an event triggered at the start of an AI agent strategy execution.
 *
 * This event captures information about the strategy being initiated, allowing
 * for tracking and analyzing the lifecycle of AI agent strategies. It provides
 * details specific to the strategy itself, such as the name, while inheriting
 * shared properties from the [DefinedFeatureEvent] superclass.
 *
 * @property strategyName The name of the strategy being started. This identifies
 * the specific strategy and is used for tracking and analysis purposes.
 * @property eventId A unique identifier for the event. By default, it is set
 * to the simple name of the event class.
 */
@Serializable
public data class AIAgentStrategyStartEvent(
    val strategyName: String,
    override val eventId: String = AIAgentStrategyStartEvent::class.simpleName!!
) : DefinedFeatureEvent()

/**
 * Event that represents the completion of an AI agent's strategy execution.
 *
 * This event captures information about the strategy that was executed and the result of its execution.
 * It is used to notify the system or consumers about the conclusion of a specific strategy.
 *
 * @property strategyName The name of the strategy that was executed.
 * @property result The result of the strategy execution, providing details such as success, failure,
 * or other status descriptions.
 * @property eventId A unique identifier for the event, defaulting to the simple name of the class.
 */
@Serializable
public data class AIAgentStrategyFinishedEvent(
    val strategyName: String,
    val result: String,
    override val eventId: String = AIAgentStrategyFinishedEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Strategy

//region Node

/**
 * Represents an event triggered when the execution of a specific AI agent node starts.
 *
 * This event captures the initial execution context of an AI agent's processing node.
 * It provides information about the node's name, the input being processed,
 * and a unique identifier for the event.
 *
 * Events of this kind are part of the feature event system and are primarily
 * used for tracking and monitoring purposes, particularly in scenarios where
 * understanding the flow of agent execution is essential.
 *
 * @property nodeName The name of the node whose execution is starting.
 * @property input The input data being processed by the node during the execution.
 * @property eventId A unique identifier for the event. Defaults to the simple name of this event class.
 */
@Serializable
public data class AIAgentNodeExecutionStartEvent(
    val nodeName: String,
    val input: String,
    override val eventId: String = AIAgentNodeExecutionStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event indicating the completion of a node's execution within an AI agent.
 *
 * This event is triggered when a specific processing node, identified by its name,
 * concludes its execution. It encapsulates details about the node's name,
 * the input it operated on, and the output it produced.
 *
 * @property nodeName The name of the node that finished execution. This provides
 * context about which part of the agent's workflow generated the event.
 * @property input The input data provided to the node. This allows tracking of
 * what the node processed during execution.
 * @property output The output generated by the node. This helps in understanding
 * the result of the node's execution.
 * @property eventId A unique identifier for the event type. Defaults to the simple
 * name of the class, enabling event categorization and tracking.
 */
@Serializable
public data class AIAgentNodeExecutionEndEvent(
    val nodeName: String,
    val input: String,
    val output: String,
    override val eventId: String = AIAgentNodeExecutionEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Node

//region LLM Call

/**
 * Represents an event indicating the start of a call to a Language Learning Model (LLM).
 *
 * This event captures the details of the LLM interaction at the point of invocation, including the
 * input prompt and any tools that will be utilized during the call. It extends the `DefinedFeatureEvent` class
 * and serves as a specific type of event in a feature-driven framework.
 *
 * @property prompt The input prompt encapsulated as a [Prompt] object. This represents the structured set of
 *                  messages and configuration parameters sent to the LLM.
 * @property tools The list of tools, represented by their string identifiers, being used within the scope
 *                 of the LLM call. These tools may extend or enhance the core functionality of the LLM.
 * @property eventId The unique identifier for this specific type of event, which defaults to the simple name of
 *                   the `LLMCallStartEvent` class.
 */
@Serializable
public data class LLMCallStartEvent(
    val prompt: Prompt,
    val tools: List<String>,
    override val eventId: String = LLMCallStartEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event signaling the completion of an LLM (Large Language Model) call.
 *
 * This event encapsulates the responses provided by the LLM during its operation. It serves as a
 * record of the responses generated by the LLM, marking the end of a particular interaction cycle.
 * The event is used within the system to capture relevant output data and ensure proper tracking
 * and logging of LLM-related interactions.
 *
 * @property responses A list of responses generated by the LLM, represented as instances of
 * [Message.Response]. Each response contains content, metadata, and additional context about the
 * interaction.
 * @property eventId The unique identifier of the event, which is set to the simple name of the
 * [LLMCallEndEvent] class by default. This is used to tag and track this type of event within the
 * system.
 */
@Serializable
public data class LLMCallEndEvent(
    val responses: List<Message.Response>,
    override val eventId: String = LLMCallEndEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion LLM Call

//region Tool Call

/**
 * Represents an event triggered when a tool is called within the system.
 *
 * This event is used to capture and describe the invocation of a tool
 * along with its associated arguments. It helps in tracking, logging,
 * or processing tool calls as part of a larger feature pipeline or system
 * workflow.
 *
 * @property toolName The unique name of the tool being called.
 * @property toolArgs The arguments provided for the tool execution.
 * @property eventId The unique identifier for this event, defaulting to the class name.
 */
@Serializable
public data class ToolCallEvent(
    val toolName: String,
    val toolArgs: ToolArgs,
    override val eventId: String = ToolCallEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event indicating that a tool encountered a validation error during its execution.
 *
 * This event captures details regarding the tool that failed validation, the arguments
 * provided to the tool, and the specific error message explaining why the validation failed.
 *
 * @property toolName The name of the tool that encountered the validation error.
 * @property toolArgs The arguments associated with the tool at the time of validation failure.
 * @property errorMessage A message describing the validation error encountered.
 * @property eventId A unique identifier for this event, defaulting to the name of the class.
 */
@Serializable
public data class ToolValidationErrorEvent(
    val toolName: String,
    val toolArgs: ToolArgs,
    val errorMessage: String,
    override val eventId: String = ToolValidationErrorEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Captures an event where a tool call has failed during its execution.
 *
 * This event is typically used to log or handle situations where a tool could not execute
 * successfully due to an error. It includes relevant details about the failed tool call,
 * such as the tool's name, the arguments provided, and the specific error encountered.
 *
 * @property toolName The name of the tool that failed.
 * @property toolArgs The arguments passed to the tool during the failed execution.
 * @property error The error encountered during the tool's execution.
 * @property eventId A unique identifier for the event, defaulting to the class name.
 */
@Serializable
public data class ToolCallFailureEvent(
    val toolName: String,
    val toolArgs: ToolArgs,
    val error: AIAgentError,
    override val eventId: String = ToolCallFailureEvent::class.simpleName!!,
) : DefinedFeatureEvent()

/**
 * Represents an event that contains the results of a tool invocation.
 *
 * This event carries information about the tool that was executed, the arguments used for its execution,
 * and the resulting outcome. It is used to track and share the details of a tool's execution within
 * the system's event-handling framework.
 *
 * @property toolName The name of the tool that was executed.
 * @property toolArgs The arguments used for executing the tool, represented as an instance of [Tool.Args].
 * @property result The result of the tool execution, which may be null if no result was produced or an error occurred.
 * @property eventId A unique identifier for this event, defaulting to the class name of `ToolCallResultEvent`.
 */
@Serializable
public data class ToolCallResultEvent(
    val toolName: String,
    val toolArgs: ToolArgs,
    val result: ToolResult?,
    override val eventId: String = ToolCallResultEvent::class.simpleName!!,
) : DefinedFeatureEvent()

//endregion Tool Call
