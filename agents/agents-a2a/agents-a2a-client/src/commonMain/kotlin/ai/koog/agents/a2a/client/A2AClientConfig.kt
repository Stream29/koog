package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.models.JsonRpcError
import ai.koog.agents.a2a.core.models.RetryPolicy
import ai.koog.agents.a2a.core.utils.A2AConstants

/**
 * Configuration for the A2A client.
 */
data class A2AClientConfig(
    val timeout: Long = A2AConstants.DEFAULT_TIMEOUT_MS,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
)

/**
 * Exception thrown by A2A client operations.
 */
class A2AClientException(
    message: String,
    val jsonRpcError: JsonRpcError? = null,
    cause: Throwable? = null
) : Exception(message, cause)