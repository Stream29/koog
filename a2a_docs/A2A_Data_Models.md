# A2A Protocol Data Models

## Core Data Structures

### Agent Card

The Agent Card describes an agent's identity, capabilities, and available skills.

```kotlin
data class AgentCard(
    val name: String,
    val description: String,
    val version: String,
    val capabilities: List<String>,
    val skills: List<Skill>,
    val metadata: Map<String, Any> = emptyMap()
)

data class Skill(
    val name: String,
    val description: String,
    val parameters: JsonSchema,
    val returns: JsonSchema? = null,
    val examples: List<SkillExample> = emptyList()
)

data class SkillExample(
    val input: Map<String, Any>,
    val output: Any,
    val description: String? = null
)
```

### Task

Tasks represent units of work with lifecycle management.

```kotlin
data class Task(
    val id: String,
    val status: TaskStatus,
    val messages: List<Message> = emptyList(),
    val artifacts: List<Artifact> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
    val metadata: Map<String, Any> = emptyMap()
)

enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

### Message

Messages represent communication turns between agents.

```kotlin
data class Message(
    val id: String,
    val taskId: String,
    val direction: MessageDirection,
    val parts: List<MessagePart>,
    val timestamp: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

enum class MessageDirection {
    INBOUND,
    OUTBOUND
}

sealed class MessagePart {
    data class Text(
        val content: String
    ) : MessagePart()
    
    data class File(
        val name: String,
        val mimeType: String,
        val content: ByteArray, // Base64 encoded in JSON
        val size: Long
    ) : MessagePart()
    
    data class StructuredData(
        val schema: String? = null,
        val data: Map<String, Any>
    ) : MessagePart()
    
    data class Reference(
        val url: String,
        val type: String,
        val description: String? = null
    ) : MessagePart()
}
```

### Artifact

Artifacts represent outputs generated during task execution.

```kotlin
data class Artifact(
    val id: String,
    val taskId: String,
    val type: ArtifactType,
    val content: Any,
    val format: String,
    val createdAt: Instant,
    val metadata: Map<String, Any> = emptyMap()
)

enum class ArtifactType {
    RESULT,
    INTERMEDIATE,
    ERROR,
    LOG,
    ATTACHMENT
}
```

## JSON-RPC Message Structures

### Request Format

```kotlin
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: Any? = null,
    val id: String
)
```

### Response Format

```kotlin
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: Any? = null,
    val error: JsonRpcError? = null,
    val id: String
)

data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: Any? = null
)
```

### Method-Specific Parameters

#### message/send Parameters

```kotlin
data class MessageSendParams(
    val message: Message,
    val taskConfig: TaskConfig? = null
)

data class TaskConfig(
    val timeout: Long? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val maxRetries: Int = 0,
    val metadata: Map<String, Any> = emptyMap()
)

enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}
```

#### message/stream Parameters

```kotlin
data class MessageStreamParams(
    val message: Message,
    val streamConfig: StreamConfig? = null
)

data class StreamConfig(
    val includeIntermediates: Boolean = true,
    val bufferSize: Int = 1024,
    val heartbeatInterval: Long = 30000
)
```

#### tasks/get Parameters

```kotlin
data class TaskGetParams(
    val taskId: String,
    val includeMessages: Boolean = false,
    val includeArtifacts: Boolean = false
)
```

#### tasks/cancel Parameters

```kotlin
data class TaskCancelParams(
    val taskId: String,
    val reason: String? = null
)
```

## Authentication Models

### Authentication Context

```kotlin
data class AuthenticationContext(
    val scheme: AuthenticationScheme,
    val credentials: Map<String, String>,
    val roles: List<String> = emptyList(),
    val scopes: List<String> = emptyList()
)

enum class AuthenticationScheme {
    API_KEY,
    BEARER_TOKEN,
    OAUTH2,
    CUSTOM
}
```

### Agent Identity

```kotlin
data class AgentIdentity(
    val id: String,
    val name: String,
    val version: String,
    val publicKey: String? = null,
    val certificateChain: List<String> = emptyList()
)
```

## Configuration Models

### Connection Configuration

```kotlin
data class A2AConnectionConfig(
    val baseUrl: String,
    val authentication: AuthenticationContext,
    val timeout: Long = 30000,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    val compression: Boolean = true,
    val keepAlive: Boolean = true
)

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
```

### Webhook Configuration

```kotlin
data class WebhookConfig(
    val url: String,
    val events: List<WebhookEvent>,
    val authentication: AuthenticationContext? = null,
    val retryPolicy: RetryPolicy = RetryPolicy.DEFAULT
)

enum class WebhookEvent {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_COMPLETED,
    TASK_FAILED,
    TASK_CANCELLED,
    MESSAGE_RECEIVED,
    ARTIFACT_CREATED
}
```

## Error Models

### A2A-Specific Errors

```kotlin
sealed class A2AError(
    val code: Int,
    val message: String,
    val data: Map<String, Any> = emptyMap()
) {
    class AgentUnavailable(
        val agentId: String,
        val retryAfter: Long? = null
    ) : A2AError(-40001, "Agent unavailable", mapOf(
        "agent_id" to agentId,
        "retry_after" to retryAfter
    ).filterValues { it != null })
    
    class TaskTimeout(
        val taskId: String,
        val timeoutMs: Long
    ) : A2AError(-40002, "Task timeout", mapOf(
        "task_id" to taskId,
        "timeout_ms" to timeoutMs
    ))
    
    class CapabilityMismatch(
        val requiredCapability: String,
        val availableCapabilities: List<String>
    ) : A2AError(-40003, "Capability mismatch", mapOf(
        "required" to requiredCapability,
        "available" to availableCapabilities
    ))
    
    class AuthenticationFailed(
        val reason: String
    ) : A2AError(-40004, "Authentication failed", mapOf(
        "reason" to reason
    ))
    
    class ResourceExhausted(
        val resource: String,
        val limit: Long,
        val current: Long
    ) : A2AError(-40005, "Resource exhausted", mapOf(
        "resource" to resource,
        "limit" to limit,
        "current" to current
    ))
}
```

## Validation Schemas

### JSON Schema Definitions

The A2A protocol uses JSON Schema for validation of structured data. Each data model should have corresponding JSON Schema definitions for:

1. **Message validation**: Ensuring message structure and content validity
2. **Skill parameter validation**: Validating input parameters for agent skills
3. **Configuration validation**: Verifying connection and webhook configurations
4. **Artifact validation**: Ensuring artifact content matches expected formats

### Schema Registry

Agents should maintain a schema registry for:
- Skill parameter schemas
- Custom artifact formats
- Extension data structures
- Metadata validation rules

This registry enables:
- Runtime validation of data structures
- API documentation generation
- Client SDK generation
- Interoperability testing