package ai.koog.agents.a2a.core.utils

/**
 * JSON-RPC method names as defined by the A2A protocol specification.
 */
object A2AMethodConstants {
    /**
     * Send a message to an agent for processing.
     */
    const val MESSAGE_SEND = "message/send"
    
    /**
     * Send a message to an agent with streaming response.
     */
    const val MESSAGE_STREAM = "message/stream"
    
    /**
     * Retrieve task status and results.
     */
    const val TASKS_GET = "tasks/get"
    
    /**
     * Cancel an ongoing task.
     */
    const val TASKS_CANCEL = "tasks/cancel"
    
    /**
     * Get detailed agent information.
     */
    const val AGENT_AUTHENTICATED_EXTENDED_CARD = "agent/authenticatedExtendedCard"
    
    /**
     * Configure webhook endpoints for task updates.
     */
    const val TASKS_PUSH_NOTIFICATION_CONFIG_SET = "tasks/pushNotificationConfig/set"
    
    /**
     * Get webhook configurations.
     */
    const val TASKS_PUSH_NOTIFICATION_CONFIG_GET = "tasks/pushNotificationConfig/get"
    
    /**
     * Remove webhook configurations.
     */
    const val TASKS_PUSH_NOTIFICATION_CONFIG_UNSUBSCRIBE = "tasks/pushNotificationConfig/unsubscribe"
    
    /**
     * Resubscribe to task notifications.
     */
    const val TASKS_RESUBSCRIBE = "tasks/resubscribe"
}