package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.AIAgentBeforeCloseEvent
import ai.koog.agents.core.feature.model.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.AIAgentRunErrorEvent
import ai.koog.agents.core.feature.model.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.AfterExecuteMultipleChoicesEvent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeExecuteMultipleChoicesEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.StartLLMStreamingEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.core.feature.model.ToolCallFailureEvent
import ai.koog.agents.core.feature.model.ToolCallResultEvent
import ai.koog.agents.core.feature.model.ToolValidationErrorEvent
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message

internal val FeatureMessage.featureMessage
    get() = "Feature message"

internal val FeatureEvent.featureEvent
    get() = "Feature event"

internal val FeatureStringMessage.featureStringMessage
    get() = "Feature string message (message: ${this.message})"

internal val AIAgentStartedEvent.agentStartedEventFormat
    get() = "${this.eventId} (agent id: ${agentId}, session id: ${sessionId}, strategy: ${this.strategyName})"

internal val AIAgentFinishedEvent.agentFinishedEventFormat
    get() = "${this.eventId} (agent id: ${this.agentId}, session id: ${this.sessionId}, result: ${this.result})"

internal val AIAgentRunErrorEvent.agentRunErrorEventFormat
    get() = "${this.eventId} (agent id: ${this.agentId}, session id: ${this.sessionId}, error: ${this.error.message})"

internal val AIAgentBeforeCloseEvent.agentBeforeCloseFormat
    get() = "$eventId (agent id: ${agentId})"

internal val AIAgentStrategyStartEvent.strategyStartEventFormat
    get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName})"

internal val AIAgentStrategyFinishedEvent.strategyFinishedEventFormat
    get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName}, result: ${result})"

internal val AIAgentNodeExecutionStartEvent.nodeExecutionStartEventFormat
    get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input})"

internal val AIAgentNodeExecutionEndEvent.nodeExecutionEndEventFormat
    get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input}, output: ${output})"

internal val BeforeLLMCallEvent.llmCallStartEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, tools: [${tools.joinToString()}])"

internal val AfterLLMCallEvent.llmCallEndEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, responses: [${responses.joinToString { it.traceString }}])"

internal val StartLLMStreamingEvent.startLLMStreamingEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model})"

internal val BeforeExecuteMultipleChoicesEvent.beforeExecuteMultipleChoicesEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, tools: [${tools.joinToString()}])"

internal val AfterExecuteMultipleChoicesEvent.afterExecuteMultipleChoicesEventFormat
    get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, responses: [${responses.joinToString { response -> "[${response.joinToString { message -> message.traceString }}]" }}])"

internal val ToolCallEvent.toolCallEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs})"

internal val ToolValidationErrorEvent.toolValidationErrorEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, validation error: ${error})"

internal val ToolCallFailureEvent.toolCallFailureEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, error: ${error.message})"

internal val ToolCallResultEvent.toolCallResultEventFormat
    get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, result: ${result})"

internal val FeatureMessage.traceMessage: String
    get() {
        return when (this) {
            is AIAgentStartedEvent               -> this.agentStartedEventFormat
            is AIAgentFinishedEvent              -> this.agentFinishedEventFormat
            is AIAgentRunErrorEvent              -> this.agentRunErrorEventFormat
            is AIAgentBeforeCloseEvent           -> this.agentBeforeCloseFormat
            is AIAgentStrategyStartEvent         -> this.strategyStartEventFormat
            is AIAgentStrategyFinishedEvent      -> this.strategyFinishedEventFormat
            is AIAgentNodeExecutionStartEvent    -> this.nodeExecutionStartEventFormat
            is AIAgentNodeExecutionEndEvent      -> this.nodeExecutionEndEventFormat
            is BeforeLLMCallEvent                -> this.llmCallStartEventFormat
            is AfterLLMCallEvent                 -> this.llmCallEndEventFormat
            is StartLLMStreamingEvent            -> this.startLLMStreamingEventFormat
            is BeforeExecuteMultipleChoicesEvent -> this.beforeExecuteMultipleChoicesEventFormat
            is AfterExecuteMultipleChoicesEvent  -> this.afterExecuteMultipleChoicesEventFormat
            is ToolCallEvent                     -> this.toolCallEventFormat
            is ToolValidationErrorEvent          -> this.toolValidationErrorEventFormat
            is ToolCallFailureEvent              -> this.toolCallFailureEventFormat
            is ToolCallResultEvent               -> this.toolCallResultEventFormat
            is FeatureStringMessage              -> this.featureStringMessage
            is FeatureEvent                      -> this.featureEvent
            else                                 -> this.featureMessage
        }
    }

internal val Prompt.traceString: String
    get() {
        val builder = StringBuilder()
            .append("id: ").append(id)
            .append(", messages: [")
            .append(messages.joinToString(", ", prefix = "{", postfix = "}") { message -> "role: ${message.role}, message: ${message.content}" })
            .append("]")
            .append(", ")
            .append("temperature: ").append(params.temperature)

        return builder.toString()
    }

internal val Message.Response.traceString: String
    get() {
        return "role: ${role}, message: $content"
    }

internal val LLModel.eventString: String
    get() = "${this.provider.id}:${this.id}"
