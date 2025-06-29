package ai.koog.agents.core.feature

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Provides a [SerializersModule] that handles polymorphic serialization and deserialization for various events
 * and messages associated with features, agents, and strategies.
 *
 * This module supports polymorphic serialization for the following base classes:
 * - [FeatureMessage]
 * - [FeatureEvent]
 * - [DefinedFeatureEvent]
 *
 * It registers the concrete subclasses of these base classes for serialization and deserialization:
 * - [AIAgentStartedEvent] - Fired when an AI agent starts execution
 * - [AIAgentFinishedEvent] - Fired when an AI agent completes execution
 * - [AIAgentBeforeCloseEvent] - Fired before an AI agent is closed
 * - [AIAgentRunErrorEvent] - Fired when an AI agent encounters a runtime error
 * - [AIAgentStrategyStartEvent] - Fired when an AI agent strategy begins
 * - [AIAgentStrategyFinishedEvent] - Fired when an AI agent strategy completes
 * - [AIAgentNodeExecutionStartEvent] - Fired when a node execution starts
 * - [AIAgentNodeExecutionEndEvent] - Fired when a node execution ends
 * - [ToolCallEvent] - Fired when a tool is called
 * - [ToolValidationErrorEvent] - Fired when tool validation fails
 * - [ToolCallFailureEvent] - Fired when a tool call fails
 * - [ToolCallResultEvent] - Fired when a tool call returns a result
 * - [BeforeLLMCallEvent] - Fired before making an LLM call
 * - [AfterLLMCallEvent] - Fired after completing an LLM call
 * - [StartLLMStreamingEvent] - Fired when LLM streaming begins
 * - [BeforeExecuteMultipleChoicesEvent] - Fired before executing multiple choice logic
 * - [AfterExecuteMultipleChoicesEvent] - Fired after executing multiple choice logic
 *
 * This configuration enables proper handling of the diverse event types encountered in the system by ensuring
 * that the polymorphic serialization framework can correctly serialize and deserialize each subclass.
 */
public val agentFeatureMessageSerializersModule: SerializersModule
    get() = SerializersModule {
            polymorphic(FeatureMessage::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
                subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(ToolCallEvent::class, ToolCallEvent.serializer())
                subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
                subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
                subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
                subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
                subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
                subclass(StartLLMStreamingEvent::class, StartLLMStreamingEvent.serializer())
                subclass(BeforeExecuteMultipleChoicesEvent::class, BeforeExecuteMultipleChoicesEvent.serializer())
                subclass(AfterExecuteMultipleChoicesEvent::class, AfterExecuteMultipleChoicesEvent.serializer())
            }

            polymorphic(FeatureEvent::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
                subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(ToolCallEvent::class, ToolCallEvent.serializer())
                subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
                subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
                subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
                subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
                subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
                subclass(StartLLMStreamingEvent::class, StartLLMStreamingEvent.serializer())
                subclass(BeforeExecuteMultipleChoicesEvent::class, BeforeExecuteMultipleChoicesEvent.serializer())
                subclass(AfterExecuteMultipleChoicesEvent::class, AfterExecuteMultipleChoicesEvent.serializer())
            }

            polymorphic(DefinedFeatureEvent::class) {
                subclass(AIAgentStartedEvent::class, AIAgentStartedEvent.serializer())
                subclass(AIAgentFinishedEvent::class, AIAgentFinishedEvent.serializer())
                subclass(AIAgentBeforeCloseEvent::class, AIAgentBeforeCloseEvent.serializer())
                subclass(AIAgentRunErrorEvent::class, AIAgentRunErrorEvent.serializer())
                subclass(AIAgentStrategyStartEvent::class, AIAgentStrategyStartEvent.serializer())
                subclass(AIAgentStrategyFinishedEvent::class, AIAgentStrategyFinishedEvent.serializer())
                subclass(AIAgentNodeExecutionStartEvent::class, AIAgentNodeExecutionStartEvent.serializer())
                subclass(AIAgentNodeExecutionEndEvent::class, AIAgentNodeExecutionEndEvent.serializer())
                subclass(ToolCallEvent::class, ToolCallEvent.serializer())
                subclass(ToolValidationErrorEvent::class, ToolValidationErrorEvent.serializer())
                subclass(ToolCallFailureEvent::class, ToolCallFailureEvent.serializer())
                subclass(ToolCallResultEvent::class, ToolCallResultEvent.serializer())
                subclass(BeforeLLMCallEvent::class, BeforeLLMCallEvent.serializer())
                subclass(AfterLLMCallEvent::class, AfterLLMCallEvent.serializer())
                subclass(StartLLMStreamingEvent::class, StartLLMStreamingEvent.serializer())
                subclass(BeforeExecuteMultipleChoicesEvent::class, BeforeExecuteMultipleChoicesEvent.serializer())
                subclass(AfterExecuteMultipleChoicesEvent::class, AfterExecuteMultipleChoicesEvent.serializer())
            }
        }
