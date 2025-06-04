package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * Response from the message/send method.
 */
@Serializable
data class MessageSendResponse(
    val task: Task
)

/**
 * Response from the message/stream method (sent as SSE events).
 */
@Serializable
data class MessageStreamResponse(
    val task: Task,
    val artifact: Artifact? = null,
    val event: StreamEvent
)

/**
 * Types of streaming events.
 */
@Serializable
enum class StreamEvent {
    TASK_STARTED,
    TASK_PROGRESS,
    ARTIFACT_CREATED,
    TASK_COMPLETED,
    TASK_FAILED
}

/**
 * Response from the tasks/get method.
 */
@Serializable
data class TaskGetResponse(
    val task: Task
)

/**
 * Response from the tasks/cancel method.
 */
@Serializable
data class TaskCancelResponse(
    val task: Task
)

/**
 * Response from setting push notification configuration.
 */
@Serializable
data class SetTaskPushNotificationConfigResponse(
    val success: Boolean,
    val webhookId: String? = null
)

/**
 * Response from getting push notification configuration.
 */
@Serializable
data class GetTaskPushNotificationConfigResponse(
    val webhookConfigs: List<WebhookConfig>
)