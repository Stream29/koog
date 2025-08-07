@file:OptIn(ExperimentalUuidApi::class, InternalAgentsApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfigBase
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.element.AgentRunInfoContextElement
import ai.koog.agents.core.agent.context.element.getAgentRunInfoElementOrThrow
import ai.koog.agents.core.agent.context.getAgentContextData
import ai.koog.agents.core.agent.entity.AIAgentStateManager
import ai.koog.agents.core.agent.entity.AIAgentStorage
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.AIAgentEnvironmentUtils.mapToToolResult
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.exception.AgentEngineException
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.PromptExecutorProxy
import ai.koog.agents.core.model.AgentServiceError
import ai.koog.agents.core.model.AgentServiceErrorType
import ai.koog.agents.core.model.message.AIAgentEnvironmentToolResultToAgentContent
import ai.koog.agents.core.model.message.AgentToolCallToEnvironmentContent
import ai.koog.agents.core.model.message.AgentToolCallsToEnvironmentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultMultipleToAgentMessage
import ai.koog.agents.core.model.message.EnvironmentToolResultToAgentContent
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

public abstract class AIAgentBaseImpl<Input, Output>(
    public val promptExecutor: PromptExecutor,
    public val agentConfig: AIAgentConfigBase,
    override val id: String = Uuid.random().toString(),
    public val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    public val clock: Clock = Clock.System,
    private val agentName: String
) : AIAgentBase<Input, Output>, AIAgentEnvironment {

    protected companion object {
        public val logger: KLogger = KotlinLogging.logger {}
    }

    protected abstract val pipeline: AIAgentPipeline

    override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()

        logger.info {
            formatLog(
                agentRunInfo.agentId,
                agentRunInfo.runId,
                "Executing tools: [${toolCalls.joinToString(", ") { it.tool }}]"
            )
        }

        val message = AgentToolCallsToEnvironmentMessage(
            runId = agentRunInfo.runId,
            content = toolCalls.map { call ->
                AgentToolCallToEnvironmentContent(
                    agentId = id,
                    runId = agentRunInfo.runId,
                    toolCallId = call.id,
                    toolName = call.tool,
                    toolArgs = call.contentJson
                )
            }
        )

        val results = processToolCallMultiple(message).mapToToolResult()
        logger.debug {
            "Received results from tools call (" +
                    "tools: [${toolCalls.joinToString(", ") { it.tool }}], " +
                    "results: [${results.joinToString(", ") { it.result?.toStringDefault() ?: "null" }}])"
        }

        return results
    }

    private suspend fun processToolCallMultiple(message: AgentToolCallsToEnvironmentMessage): EnvironmentToolResultMultipleToAgentMessage {
        // call tools in parallel and return results
        val results = supervisorScope {
            message.content
                .map { call -> async { processToolCall(call) } }
                .awaitAll()
        }

        return EnvironmentToolResultMultipleToAgentMessage(
            runId = message.runId,
            content = results
        )
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun processToolCall(content: AgentToolCallToEnvironmentContent): EnvironmentToolResultToAgentContent =
        allowToolCalls {
            logger.debug { "Handling tool call sent by server..." }
            val tool = toolRegistry.getTool(content.toolName)
            // Tool Args
            val toolArgs = try {
                tool.decodeArgs(content.toolArgs)
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to parse arguments: ${content.toolArgs}" }
                return toolResult(
                    message = "Tool \"${tool.name}\" failed to parse arguments because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentName,
                    result = null
                )
            }

            pipeline.onToolCall(
                runId = content.runId,
                toolCallId = content.toolCallId,
                tool = tool,
                toolArgs = toolArgs
            )

            // Tool Execution
            val toolResult = try {
                @Suppress("UNCHECKED_CAST")
                (tool as Tool<ToolArgs, ToolResult>).execute(toolArgs, toolEnabler)
            } catch (e: ToolException) {

                pipeline.onToolValidationError(
                    runId = content.runId,
                    toolCallId = content.toolCallId,
                    tool = tool,
                    toolArgs = toolArgs,
                    error = e.message
                )

                return toolResult(
                    message = e.message,
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentName,
                    result = null
                )
            } catch (e: Exception) {
                logger.error(e) { "Tool \"${tool.name}\" failed to execute with arguments: ${content.toolArgs}" }
                pipeline.onToolCallFailure(
                    runId = content.runId,
                    toolCallId = content.toolCallId,
                    tool = tool,
                    toolArgs = toolArgs,
                    throwable = e
                )

                return toolResult(
                    message = "Tool \"${tool.name}\" failed to execute because of ${e.message}!",
                    toolCallId = content.toolCallId,
                    toolName = content.toolName,
                    agentId = agentName,
                    result = null
                )
            }

            // Tool Finished with Result
            pipeline.onToolCallResult(
                runId = content.runId,
                toolCallId = content.toolCallId,
                tool = tool,
                toolArgs = toolArgs,
                result = toolResult
            )

            logger.debug { "Completed execution of ${content.toolName} with result: $toolResult" }

            return toolResult(
                toolCallId = content.toolCallId,
                toolName = content.toolName,
                agentId = agentName,
                message = toolResult.toStringDefault(),
                result = toolResult
            )
        }

    private fun toolResult(
        toolCallId: String?,
        toolName: String,
        agentId: String,
        message: String,
        result: ToolResult?
    ): EnvironmentToolResultToAgentContent = AIAgentEnvironmentToolResultToAgentContent(
        toolCallId = toolCallId,
        toolName = toolName,
        agentId = agentId,
        message = message,
        toolResult = result
    )

    override suspend fun reportProblem(exception: Throwable) {
        val agentRunInfo = currentCoroutineContext().getAgentRunInfoElementOrThrow()

        logger.error(exception) {
            formatLog(agentRunInfo.agentId, agentRunInfo.runId, "Reporting problem: ${exception.message}")
        }

        processError(
            agentId = agentRunInfo.agentId,
            runId = agentRunInfo.runId,
            error = AgentServiceError(
                type = AgentServiceErrorType.UNEXPECTED_ERROR,
                message = exception.message ?: "unknown error"
            )
        )
    }

    private suspend fun processError(agentId: String, runId: String, error: AgentServiceError) {
        try {
            throw error.asException()
        } catch (e: AgentEngineException) {
            logger.error(e) { "Execution exception reported by server!" }
            pipeline.onAgentRunError(agentId = agentId, runId = runId, throwable = e)
        }
    }

    protected fun formatLog(agentId: String, runId: String, message: String): String =
        "[agent id: $agentId, run id: $runId] $message"
}