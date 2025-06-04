package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.A2AClient
import ai.koog.agents.a2a.core.models.*
import ai.koog.agents.a2a.core.utils.A2AConstants
import ai.koog.agents.a2a.core.utils.A2AMethodConstants
import ai.koog.agents.a2a.core.utils.IdGenerator
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ktor-based implementation of the A2A client.
 * Supports both JVM and JS platforms.
 */
class KtorA2AClient(
    httpClient: HttpClient,
    private val config: A2AClientConfig = A2AClientConfig()
) : A2AClient {

    private val httpClient = httpClient.config {
        install(SSE)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun sendMessage(
        agentUrl: String,
        message: Message,
        taskConfig: TaskConfig?,
        authentication: AuthenticationContext?
    ): Task {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.MESSAGE_SEND,
            params = json.encodeToJsonElement(MessageSendParams(message, taskConfig)),
            id = IdGenerator.generateRequestId()
        )

        val response = makeJsonRpcRequest(agentUrl, request, authentication)
        
        val error = response.error
        if (error != null) {
            throw A2AClientException("JSON-RPC error: ${error.message}", error)
        }

        val sendResponse = json.decodeFromJsonElement<MessageSendResponse>(
            response.result ?: throw A2AClientException("No result in response")
        )

        // For synchronous operation, poll until task completion
        return pollTaskUntilCompletion(agentUrl, sendResponse.task.id, authentication)
    }

    override suspend fun sendMessageStream(
        agentUrl: String,
        message: Message,
        streamConfig: StreamConfig?,
        authentication: AuthenticationContext?
    ): Flow<MessageStreamResponse> = flow {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.MESSAGE_STREAM,
            params = json.encodeToJsonElement(MessageStreamParams(message, streamConfig)),
            id = IdGenerator.generateRequestId()
        )

        val url = normalizeUrl(agentUrl) + A2AConstants.A2A_ENDPOINT_PATH
        
        try {
            httpClient.sse(
                request = {
                    url(url)
                    method = HttpMethod.Post
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    authentication?.let { applyAuthentication(it) }
                }
            ) {
                incoming.collect { event ->
                    // Parse the SSE data as MessageStreamResponse
                    event.data?.let { data ->
                        try {
                            val streamResponse = json.decodeFromString<MessageStreamResponse>(data)
                            emit(streamResponse)
                        } catch (e: Exception) {
                            // Handle JSON parsing errors - could be error responses
                            try {
                                val jsonRpcResponse = json.decodeFromString<JsonRpcResponse>(data)
                                jsonRpcResponse.error?.let { error ->
                                    throw A2AClientException("SSE JSON-RPC error: ${error.message}", error)
                                }
                            } catch (_: Exception) {
                                // If it's not a valid JSON-RPC response either, throw original parsing error
                                throw A2AClientException("Failed to parse SSE event data: ${e.message}", cause = e)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback to polling if SSE fails
            val task = sendMessage(agentUrl, message, null, authentication)
            emit(MessageStreamResponse(
                task = task,
                artifact = null,
                event = if (task.status == TaskStatus.COMPLETED) StreamEvent.TASK_COMPLETED else StreamEvent.TASK_STARTED
            ))
        }
    }

    override suspend fun getTask(
        agentUrl: String,
        taskId: String,
        includeMessages: Boolean,
        includeArtifacts: Boolean,
        authentication: AuthenticationContext?
    ): Task {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.TASKS_GET,
            params = json.encodeToJsonElement(TaskGetParams(taskId, includeMessages, includeArtifacts)),
            id = IdGenerator.generateRequestId()
        )

        val response = makeJsonRpcRequest(agentUrl, request, authentication)
        
        val error = response.error
        if (error != null) {
            throw A2AClientException("JSON-RPC error: ${error.message}", error)
        }

        val getResponse = json.decodeFromJsonElement<TaskGetResponse>(
            response.result ?: throw A2AClientException("No result in response")
        )

        return getResponse.task
    }

    override suspend fun cancelTask(
        agentUrl: String,
        taskId: String,
        reason: String?,
        authentication: AuthenticationContext?
    ): Task {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.TASKS_CANCEL,
            params = json.encodeToJsonElement(TaskCancelParams(taskId, reason)),
            id = IdGenerator.generateRequestId()
        )

        val response = makeJsonRpcRequest(agentUrl, request, authentication)
        
        val error = response.error
        if (error != null) {
            throw A2AClientException("JSON-RPC error: ${error.message}", error)
        }

        val cancelResponse = json.decodeFromJsonElement<TaskCancelResponse>(
            response.result ?: throw A2AClientException("No result in response")
        )

        return cancelResponse.task
    }

    override suspend fun getAgentCard(
        agentUrl: String,
        authentication: AuthenticationContext?
    ): AgentCard {
        val url = normalizeUrl(agentUrl) + A2AConstants.AGENT_CARD_ENDPOINT
        
        val response = httpClient.get(url) {
            authentication?.let { applyAuthentication(it) }
        }

        return response.body<AgentCard>()
    }

    override suspend fun setTaskPushNotificationConfig(
        agentUrl: String,
        webhookConfig: WebhookConfig,
        authentication: AuthenticationContext?
    ): SetTaskPushNotificationConfigResponse {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.TASKS_PUSH_NOTIFICATION_CONFIG_SET,
            params = json.encodeToJsonElement(SetTaskPushNotificationConfigParams(webhookConfig)),
            id = IdGenerator.generateRequestId()
        )

        val response = makeJsonRpcRequest(agentUrl, request, authentication)
        
        val error = response.error
        if (error != null) {
            throw A2AClientException("JSON-RPC error: ${error.message}", error)
        }

        return json.decodeFromJsonElement<SetTaskPushNotificationConfigResponse>(
            response.result ?: throw A2AClientException("No result in response")
        )
    }

    override suspend fun getTaskPushNotificationConfig(
        agentUrl: String,
        webhookUrl: String?,
        authentication: AuthenticationContext?
    ): GetTaskPushNotificationConfigResponse {
        val request = JsonRpcRequest(
            method = A2AMethodConstants.TASKS_PUSH_NOTIFICATION_CONFIG_GET,
            params = json.encodeToJsonElement(GetTaskPushNotificationConfigParams(webhookUrl)),
            id = IdGenerator.generateRequestId()
        )

        val response = makeJsonRpcRequest(agentUrl, request, authentication)
        
        val error = response.error
        if (error != null) {
            throw A2AClientException("JSON-RPC error: ${error.message}", error)
        }

        return json.decodeFromJsonElement<GetTaskPushNotificationConfigResponse>(
            response.result ?: throw A2AClientException("No result in response")
        )
    }

    override suspend fun close() {
        httpClient.close()
    }

    private suspend fun makeJsonRpcRequest(
        agentUrl: String,
        request: JsonRpcRequest,
        authentication: AuthenticationContext?
    ): JsonRpcResponse {
        val url = normalizeUrl(agentUrl) + A2AConstants.A2A_ENDPOINT_PATH
        
        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
            authentication?.let { applyAuthentication(it) }
        }

        return response.body<JsonRpcResponse>()
    }

    private suspend fun pollTaskUntilCompletion(
        agentUrl: String,
        taskId: String,
        authentication: AuthenticationContext?
    ): Task {
        var attempts = 0
        val retryPolicy = config.retryPolicy
        val maxAttempts = retryPolicy.maxAttempts
        
        while (attempts < maxAttempts) {
            val task = getTask(agentUrl, taskId, includeMessages = true, includeArtifacts = true, authentication)
            
            when (task.status) {
                TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED -> {
                    return task
                }
                TaskStatus.PENDING, TaskStatus.RUNNING -> {
                    attempts++
                    if (attempts < maxAttempts) {
                        delay(retryPolicy.initialDelay * attempts)
                    }
                }
            }
        }
        
        throw A2AClientException("Task polling timeout after $maxAttempts attempts")
    }

    private fun HttpRequestBuilder.applyAuthentication(auth: AuthenticationContext) {
        when (auth.scheme) {
            AuthenticationScheme.API_KEY -> {
                auth.credentials["apiKey"]?.let { apiKey ->
                    headers.append(A2AAuthConstants.API_KEY_HEADER, apiKey)
                }
            }
            AuthenticationScheme.BEARER_TOKEN -> {
                auth.credentials["token"]?.let { token ->
                    headers.append(A2AAuthConstants.AUTH_HEADER, "${A2AAuthConstants.BEARER_PREFIX}$token")
                }
            }
            AuthenticationScheme.OAUTH2 -> {
                auth.credentials["accessToken"]?.let { token ->
                    headers.append(A2AAuthConstants.AUTH_HEADER, "${A2AAuthConstants.BEARER_PREFIX}$token")
                }
            }
            AuthenticationScheme.OPENID_CONNECT -> {
                auth.credentials["idToken"]?.let { token ->
                    headers.append(A2AAuthConstants.AUTH_HEADER, "${A2AAuthConstants.BEARER_PREFIX}$token")
                }
            }
            AuthenticationScheme.CUSTOM -> {
                // Apply custom headers from credentials map
                auth.credentials.forEach { (key, value) ->
                    headers.append(key, value)
                }
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        return url.trimEnd('/')
    }
}

