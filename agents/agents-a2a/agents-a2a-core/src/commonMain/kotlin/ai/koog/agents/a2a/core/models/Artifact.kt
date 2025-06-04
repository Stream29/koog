package ai.koog.agents.a2a.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents outputs generated during task execution as defined by the A2A protocol.
 */
@Serializable
data class Artifact(
    val id: String,
    val taskId: String,
    val type: ArtifactType,
    val content: String, // JSON-serializable content
    val format: String,
    val createdAt: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Types of artifacts that can be generated during task execution.
 */
@Serializable
enum class ArtifactType {
    RESULT,
    INTERMEDIATE,
    ERROR,
    LOG,
    ATTACHMENT
}