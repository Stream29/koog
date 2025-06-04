package ai.koog.agents.a2a.core

import ai.koog.agents.a2a.core.models.*
import kotlinx.coroutines.flow.Flow

/**
 * Core interface for A2A client implementations.
 * Defines the contract for communicating with A2A-compliant agents.
 */
interface A2AClient {
    /**
     * Send a message to an agent and wait for the task to complete.
     * This is a synchronous operation that polls for task completion.
     */
    suspend fun sendMessage(
        agentUrl: String,
        message: Message,
        taskConfig: TaskConfig? = null,
        authentication: AuthenticationContext? = null
    ): Task
    
    /**
     * Send a message to an agent with streaming response.
     * Returns a flow of streaming events including task progress and artifacts.
     */
    suspend fun sendMessageStream(
        agentUrl: String,
        message: Message,
        streamConfig: StreamConfig? = null,
        authentication: AuthenticationContext? = null
    ): Flow<MessageStreamResponse>
    
    /**
     * Get the current status and details of a specific task.
     */
    suspend fun getTask(
        agentUrl: String,
        taskId: String,
        includeMessages: Boolean = false,
        includeArtifacts: Boolean = false,
        authentication: AuthenticationContext? = null
    ): Task
    
    /**
     * Cancel an ongoing task.
     */
    suspend fun cancelTask(
        agentUrl: String,
        taskId: String,
        reason: String? = null,
        authentication: AuthenticationContext? = null
    ): Task
    
    /**
     * Retrieve the agent card from the specified agent.
     */
    suspend fun getAgentCard(
        agentUrl: String,
        authentication: AuthenticationContext? = null
    ): AgentCard
    
    /**
     * Configure push notification webhook for task updates.
     */
    suspend fun setTaskPushNotificationConfig(
        agentUrl: String,
        webhookConfig: WebhookConfig,
        authentication: AuthenticationContext? = null
    ): SetTaskPushNotificationConfigResponse
    
    /**
     * Get current push notification webhook configurations.
     */
    suspend fun getTaskPushNotificationConfig(
        agentUrl: String,
        webhookUrl: String? = null,
        authentication: AuthenticationContext? = null
    ): GetTaskPushNotificationConfigResponse
    
    /**
     * Close the client and clean up resources.
     */
    suspend fun close()
}