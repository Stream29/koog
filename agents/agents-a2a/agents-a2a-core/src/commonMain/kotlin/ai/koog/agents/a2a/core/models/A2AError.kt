package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * A2A-specific error types as defined by the protocol specification.
 */
@Serializable
sealed class A2AError(
    val code: Int,
    val message: String,
    val data: Map<String, String> = emptyMap()
) {
    /**
     * Agent is currently unavailable for processing requests.
     */
    @Serializable
    data class AgentUnavailable(
        val agentId: String,
        val retryAfter: Long? = null
    ) : A2AError(
        A2AErrorCodes.AGENT_UNAVAILABLE, 
        "Agent unavailable", 
        buildMap {
            put("agent_id", agentId)
            retryAfter?.let { put("retry_after", it.toString()) }
        }
    )
    
    /**
     * Task execution exceeded the specified timeout.
     */
    @Serializable
    data class TaskTimeout(
        val taskId: String,
        val timeoutMs: Long
    ) : A2AError(
        A2AErrorCodes.TASK_TIMEOUT,
        "Task timeout",
        mapOf(
            "task_id" to taskId,
            "timeout_ms" to timeoutMs.toString()
        )
    )
    
    /**
     * Required capability is not available on the target agent.
     */
    @Serializable
    data class CapabilityMismatch(
        val requiredCapability: String,
        val availableCapabilities: List<String>
    ) : A2AError(
        A2AErrorCodes.CAPABILITY_MISMATCH,
        "Capability mismatch",
        mapOf(
            "required" to requiredCapability,
            "available" to availableCapabilities.joinToString(",")
        )
    )
    
    /**
     * Authentication failed for the request.
     */
    @Serializable
    data class AuthenticationFailed(
        val reason: String
    ) : A2AError(
        A2AErrorCodes.AUTHENTICATION_FAILED,
        "Authentication failed",
        mapOf("reason" to reason)
    )
    
    /**
     * Resource limits have been exceeded.
     */
    @Serializable
    data class ResourceExhausted(
        val resource: String,
        val limit: Long,
        val current: Long
    ) : A2AError(
        A2AErrorCodes.RESOURCE_EXHAUSTED,
        "Resource exhausted",
        mapOf(
            "resource" to resource,
            "limit" to limit.toString(),
            "current" to current.toString()
        )
    )
}