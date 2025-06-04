package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * Parameters for the message/send method.
 */
@Serializable
data class MessageSendParams(
    val message: Message,
    val taskConfig: TaskConfig? = null
)

/**
 * Parameters for the message/stream method.
 */
@Serializable
data class MessageStreamParams(
    val message: Message,
    val streamConfig: StreamConfig? = null
)

/**
 * Parameters for the tasks/get method.
 */
@Serializable
data class TaskGetParams(
    val taskId: String,
    val includeMessages: Boolean = false,
    val includeArtifacts: Boolean = false
)

/**
 * Parameters for the tasks/cancel method.
 */
@Serializable
data class TaskCancelParams(
    val taskId: String,
    val reason: String? = null
)

/**
 * Parameters for setting push notification configuration.
 */
@Serializable
data class SetTaskPushNotificationConfigParams(
    val webhookConfig: WebhookConfig
)

/**
 * Parameters for getting push notification configuration.
 */
@Serializable
data class GetTaskPushNotificationConfigParams(
    val webhookUrl: String? = null
)