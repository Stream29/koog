package ai.koog.agents.a2a.core.utils

import com.benasher44.uuid.uuid4

/**
 * Utility for generating unique IDs for A2A protocol entities.
 */
object IdGenerator {
    /**
     * Generate a unique task ID.
     */
    fun generateTaskId(): String = "task_${uuid4()}"
    
    /**
     * Generate a unique message ID.
     */
    fun generateMessageId(): String = "msg_${uuid4()}"
    
    /**
     * Generate a unique artifact ID.
     */
    fun generateArtifactId(): String = "artifact_${uuid4()}"
    
    /**
     * Generate a unique request ID for JSON-RPC.
     */
    fun generateRequestId(): String = "req_${uuid4()}"
    
    /**
     * Generate a unique webhook ID.
     */
    fun generateWebhookId(): String = "webhook_${uuid4()}"
}