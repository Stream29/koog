package ai.koog.agents.features.opentelemetry.feature.interceptor

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.interceptor.processors.NewOTelTraceEventProcessor
import io.opentelemetry.api.trace.Tracer
import kotlin.uuid.ExperimentalUuidApi

/**
 *
 */
public class OpenTelemetryAiAgentPipelineInterceptor(
    private val interceptContext: InterceptContext<OpenTelemetry>,
    private val tracer: Tracer
) {
    /**
     *
     */
    public fun configure(pipeline: AIAgentPipeline) {
        
    }

    /**
     *
     */
    @OptIn(ExperimentalUuidApi::class)
    public fun interceptCalls(pipeline: AIAgentPipeline) {
        val processMessage = createMessageProcessor(NewOTelTraceEventProcessor(tracer))

        // copy-pasted from the trace feature
        pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{
            val event = AIAgentStartedEvent(strategyName = strategy.name)
            readStrategy { stages -> processMessage(event) }
        }

        pipeline.interceptAgentFinished(interceptContext) intercept@{ strategyName, result ->
            val event = AIAgentFinishedEvent(
                strategyName = strategyName,
                result = result,
            )
            processMessage(event)
        }

        pipeline.interceptAgentRunError(interceptContext) intercept@{ strategyName, sessionUuid, throwable ->
            val event = AIAgentRunErrorEvent(
                strategyName = strategyName,
                error = throwable.toAgentError(),
            )
            processMessage(event)
        }

        //endregion Intercept Agent Events

        //region Intercept Strategy Events

        pipeline.interceptStrategyStarted(interceptContext) intercept@{
            val event = AIAgentStrategyStartEvent(
                strategyName = strategy.name,
            )
            readStrategy { stages ->
                processMessage(event)
            }
        }

        pipeline.interceptStrategyFinished(interceptContext) intercept@{ result ->
            val event = AIAgentStrategyFinishedEvent(
                strategyName = strategy.name,
                result = result,
            )
            processMessage(event)
        }

        //endregion Intercept Strategy Events

        //region Intercept Node Events

        pipeline.interceptBeforeNode(interceptContext) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
            val event = AIAgentNodeExecutionStartEvent(
                nodeName = node.name,
                input = input?.toString() ?: ""
            )
            processMessage(event)
        }

        pipeline.interceptAfterNode(interceptContext) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
            val event = AIAgentNodeExecutionEndEvent(
                nodeName = node.name,
                input = input?.toString() ?: "",
                output = output?.toString() ?: ""
            )
            processMessage(event)
        }

        //endregion Intercept Node Events

        //region Intercept LLM Call Events

        pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ prompt, tools, model, sessionUuid ->
            val event = LLMCallStartEvent(
                prompt = prompt,
                tools = tools.map { it.name }
            )
            processMessage(event)
        }

        pipeline.interceptAfterLLMCall(interceptContext) intercept@{ prompt, tools, model, responses, sessionUuid ->
            val event = LLMCallEndEvent(
                responses = responses
            )
            processMessage(event)
        }

        //endregion Intercept LLM Call Events

        //region Intercept Tool Call Events

        pipeline.interceptToolCall(interceptContext) intercept@{ tool, toolArgs ->
            val event = ToolCallEvent(
                toolName = tool.name,
                toolArgs = toolArgs
            )
            processMessage(event)
        }

        pipeline.interceptToolValidationError(interceptContext) intercept@{ tool, toolArgs, value ->
            val event = ToolValidationErrorEvent(
                toolName = tool.name,
                toolArgs = toolArgs,
                errorMessage = value
            )
            processMessage(event)
        }

        pipeline.interceptToolCallFailure(interceptContext) intercept@{ tool, toolArgs, throwable ->
            val event = ToolCallFailureEvent(
                toolName = tool.name,
                toolArgs = toolArgs,
                error = throwable.toAgentError()
            )
            processMessage(event)
        }

        pipeline.interceptToolCallResult(interceptContext) intercept@{ tool, toolArgs, result ->
            val event = ToolCallResultEvent(
                toolName = tool.name,
                toolArgs = toolArgs,
                result = result
            )
            processMessage(event)
        }

        //endregion Intercept Tool Call Events
    }
    
    private fun createMessageProcessor(messageProcessor: FeatureMessageProcessor): suspend (FeatureMessage) -> Unit {
        return { message -> messageProcessor.processMessage(message)}
    }

}