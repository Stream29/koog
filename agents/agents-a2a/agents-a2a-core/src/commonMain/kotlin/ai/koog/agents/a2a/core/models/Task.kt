package ai.koog.agents.a2a.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a unit of work with lifecycle management as defined by the A2A protocol.
 */
@Serializable
data class Task(
    val id: String,
    val status: TaskStatus,
    val messages: List<Message> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Task lifecycle states as defined by the A2A protocol.
 */
@Serializable
enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Configuration for task execution.
 */
@Serializable
data class TaskConfig(
    val timeout: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val maxRetries: Int = 0,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Task priority levels.
 */
@Serializable
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}