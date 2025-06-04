package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * Connection configuration for A2A clients.
 */
@Serializable
data class A2AConnectionConfig(
    val baseUrl: String,
    val authentication: AuthenticationContext,
    val timeout: Long = 30000,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val compression: Boolean = true,
    val keepAlive: Boolean = true
)

/**
 * Retry policy configuration for failed requests.
 */
@Serializable
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val backoffMultiplier: Double = 2.0,
    val initialDelay: Long = 1000,
    val maxDelay: Long = 30000
) {
    companion object {
        val DEFAULT = RetryPolicy()
        val NO_RETRY = RetryPolicy(maxAttempts = 1)
    }
}

/**
 * Webhook configuration for push notifications.
 */
@Serializable
data class WebhookConfig(
    val url: String,
    val events: List<WebhookEvent>,
    val authentication: AuthenticationContext? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
)

/**
 * Events that can trigger webhook notifications.
 */
@Serializable
enum class WebhookEvent {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,
    MESSAGE_RECEIVED,
    ARTIFACT_CREATED
}

/**
 * Configuration for streaming responses.
 */
@Serializable
data class StreamConfig(
    val includeIntermediates: Boolean = true,
    val bufferSize: Int = 1024,
    val heartbeatInterval: Long = 30000
)