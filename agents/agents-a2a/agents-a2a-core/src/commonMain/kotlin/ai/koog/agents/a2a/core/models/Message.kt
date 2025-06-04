package ai.koog.agents.a2a.core.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a communication turn between agents as defined by the A2A protocol.
 */
@Serializable
data class Message(
    val id: String,
    val taskId: String,
    val direction: MessageDirection,
    val parts: List<MessagePart>,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Direction of message flow.
 */
@Serializable
enum class MessageDirection {
    INBOUND,
    OUTBOUND
}

/**
 * Different types of content that can be included in a message.
 */
@Serializable
sealed class MessagePart {
    /**
     * Plain text content.
     */
    @Serializable
    data class Text(
        val content: String
    ) : MessagePart()
    
    /**
     * Binary file content (Base64 encoded in JSON transport).
     */
    @Serializable
    data class File(
        val name: String,
        val mimeType: String,
        val content: ByteArray, // Will be Base64 encoded during serialization
        val size: Long
    ) : MessagePart() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as File

            if (name != other.name) return false
            if (mimeType != other.mimeType) return false
            if (!content.contentEquals(other.content)) return false
            if (size != other.size) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + mimeType.hashCode()
            result = 31 * result + content.contentHashCode()
            result = 31 * result + size.hashCode()
            return result
        }
    }
    
    /**
     * Structured data with optional schema validation.
     */
    @Serializable
    data class StructuredData(
        val schema: String? = null,
        val data: Map<String, String>
    ) : MessagePart()
    
    /**
     * Reference to external resources.
     */
    @Serializable
    data class Reference(
        val url: String,
        val resourceType: String,
        val description: String? = null
    ) : MessagePart()
}