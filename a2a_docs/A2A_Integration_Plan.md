# A2A Protocol Integration Plan for Koog Agents Framework

## Executive Summary

This document outlines a phased approach to integrate the Agent-to-Agent (A2A) protocol into the Koog Agents framework. The integration will enable Koog agents to communicate with other A2A-compliant agents, expanding the framework's capabilities for multi-agent collaboration.

## Integration Strategy

### Design Principles

1. **Non-Intrusive Integration**: Leverage existing framework components without breaking changes
2. **Feature-Based Architecture**: Implement A2A as optional features using the existing feature system
3. **Type Safety**: Maintain Kotlin's type safety throughout the A2A implementation
4. **Backward Compatibility**: Ensure existing agent code continues to work unchanged
5. **Gradual Adoption**: Allow incremental adoption of A2A capabilities

### Integration Points

Based on the current Koog architecture analysis, the following integration points have been identified:

1. **AIAgentFeature System**: Implement A2A as installable features
2. **Tool Infrastructure**: Expose remote agents as tools via `Tool<TArgs, TResult>`
3. **Environment Extension**: Extend `AIAgentEnvironment` for A2A message handling
4. **Message System**: Leverage existing message infrastructure for A2A communication
5. **Session Management**: Use UUID-based sessions for A2A task correlation
6. **Event Pipeline**: Use the comprehensive event system for A2A lifecycle management

## Phase 1: MVP Implementation

### Scope
**Goal**: Basic A2A client functionality with remote agent tool integration

**Duration**: 4-6 weeks

### 1.1 Core A2A Client Infrastructure

#### A2A Core Module Strategy

**IMPORTANT**: Create a shared `agents-a2a-core` module containing common definitions, interfaces, and data structures shared between client and server implementations. This prevents code duplication and ensures consistency.

#### New Modules: `agents-a2a-core` and `agents-a2a-client`
```
agents/
└── agents-a2a/
    ├── agents-a2a-core/            # Common A2A definitions and interfaces
    │   ├── Module.md
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/ai/koog/agents/a2a/core/
    │       └── commonTest/kotlin/ai/koog/agents/a2a/core/
    ├── agents-a2a-client/
    │   ├── Module.md
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/ai/koog/agents/a2a/client/
    │       ├── jvmMain/kotlin/ai/koog/agents/a2a/client/
    │       └── commonTest/kotlin/ai/koog/agents/a2a/client/
```

**Key Components**:

1. **A2AClient** - Core client for JSON-RPC communication
   ```kotlin
   interface A2AClient {
       suspend fun sendMessage(agentUrl: String, message: A2AMessage): A2AResponse
       suspend fun getTask(agentUrl: String, taskId: String): A2ATask
       suspend fun cancelTask(agentUrl: String, taskId: String): Boolean
   }
   ```

2. **A2AConnectionManager** - Connection pooling and retry logic
3. **A2AMessageSerializer** - JSON-RPC serialization/deserialization
4. **A2AAuthenticationProvider** - Authentication handling

### 1.2 Remote Agent Tool Integration

#### RemoteAgentTool Implementation
Extend the existing `Tool<TArgs, TResult>` system to support remote agents:

```kotlin
class RemoteAgentTool<TArgs : Any, TResult : Any>(
    private val agentUrl: String,
    private val skill: String,
    private val client: A2AClient,
    private val argsSerializer: KSerializer<TArgs>,
    private val resultSerializer: KSerializer<TResult>
) : Tool<TArgs, TResult>() {
    
    override suspend fun AIAgentEnvironment.execute(args: TArgs): TResult {
        val message = A2AMessage(
            parts = listOf(A2AMessagePart.StructuredData(
                data = Json.encodeToJsonElement(argsSerializer, args).jsonObject.toMap()
            ))
        )
        
        val response = client.sendMessage(agentUrl, message)
        return Json.decodeFromJsonElement(resultSerializer, response.result)
    }
}
```

#### Tool Registration Enhancement
Extend `ToolRegistry` to support remote agent discovery:

```kotlin
class A2AToolRegistry(
    private val client: A2AClient,
    private val baseRegistry: ToolRegistry = ToolRegistry.builder().build()
) : ToolRegistry {
    
    suspend fun discoverAndRegisterAgent(agentUrl: String): A2AToolRegistry {
        val agentCard = client.getAgentCard(agentUrl)
        val tools = agentCard.skills.map { skill ->
            RemoteAgentTool(
                agentUrl = agentUrl,
                skill = skill.name,
                client = client,
                // Type information from skill schema
            )
        }
        return A2AToolRegistry(client, baseRegistry + tools)
    }
}
```

### 1.3 A2A Client Feature

#### Feature Implementation
Create an `A2AClientFeature` that can be installed on agents:

```kotlin
class A2AClientFeature private constructor(
    private val config: Config
) : AIAgentFeature<A2AClientFeature.Config, A2AClientFeature> {
    
    data class Config(
        val client: A2AClient,
        val knownAgents: Map<String, String> = emptyMap(), // name -> URL
        val autoDiscovery: Boolean = false
    )
    
    override fun install(
        builder: AIAgentPipeline.Builder,
        config: Config
    ): A2AClientFeature {
        builder.interceptToolRegistryProvider { original ->
            if (config.autoDiscovery) {
                // Discover and register remote agents
                A2AToolRegistry(config.client, original.provide())
            } else {
                original
            }
        }
        return A2AClientFeature(config)
    }
    
    companion object : AIAgentFeature.Companion<Config, A2AClientFeature> {
        override fun create() = A2AClientFeature(Config(NoOpA2AClient))
    }
}
```

### 1.4 Integration with Existing Components

#### Environment Extension
Extend the environment interface to support A2A context:

```kotlin
interface A2AAgentEnvironment : AIAgentEnvironment {
    val a2aClient: A2AClient
    val remoteAgents: Map<String, String> // name -> URL
}
```

#### Session Correlation
Use existing UUID-based session management for A2A task correlation:

```kotlin
class A2ASessionManager {
    private val sessionToTaskMap = ConcurrentHashMap<UUID, String>()
    
    fun mapSessionToA2ATask(sessionId: UUID, taskId: String) {
        sessionToTaskMap[sessionId] = taskId
    }
    
    fun getA2ATaskForSession(sessionId: UUID): String? {
        return sessionToTaskMap[sessionId]
    }
}
```

### 1.5 MVP Deliverables

1. **A2A Client Library**: Basic JSON-RPC client with authentication
2. **Remote Agent Tools**: Integration with existing tool system
3. **Client Feature**: Installable feature for A2A client capabilities
4. **Documentation**: Integration guide and examples
5. **Tests**: Unit and integration tests for MVP functionality

### 1.6 MVP Limitations

- **Client-only**: No server implementation
- **Synchronous only**: No streaming or async communication
- **Basic authentication**: Limited to API key authentication
- **No caching**: No response caching or connection pooling
- **Limited error handling**: Basic retry logic only

## Phase 2: Enhanced Client Features

### Scope
**Goal**: Advanced client features with streaming and async support

**Duration**: 3-4 weeks

### 2.1 Streaming Communication

#### Streaming Message Support
Implement Server-Sent Events (SSE) for real-time communication:

```kotlin
interface A2AStreamingClient : A2AClient {
    fun streamMessage(
        agentUrl: String, 
        message: A2AMessage
    ): Flow<A2AStreamEvent>
}

sealed class A2AStreamEvent {
    data class MessagePart(val part: A2AMessagePart) : A2AStreamEvent()
    data class TaskUpdate(val task: A2ATask) : A2AStreamEvent()
    data class ArtifactCreated(val artifact: A2AArtifact) : A2AStreamEvent()
    object StreamComplete : A2AStreamEvent()
    data class StreamError(val error: A2AError) : A2AStreamEvent()
}
```

#### Streaming Tool Support
Extend remote agent tools to support streaming responses:

```kotlin
class StreamingRemoteAgentTool<TArgs : Any, TResult : Any>(
    // ... existing parameters
    private val streamingClient: A2AStreamingClient
) : Tool<TArgs, Flow<TResult>>() {
    
    override suspend fun AIAgentEnvironment.execute(args: TArgs): Flow<TResult> {
        return streamingClient.streamMessage(agentUrl, createMessage(args))
            .filterIsInstance<A2AStreamEvent.MessagePart>()
            .map { deserializeResult(it.part) }
    }
}
```

### 2.2 Asynchronous Communication

#### Webhook Support
Implement webhook configuration for long-running tasks:

```kotlin
class A2AWebhookManager(
    private val webhookUrl: String,
    private val authProvider: A2AAuthenticationProvider
) {
    suspend fun configureWebhook(
        agentUrl: String,
        events: List<A2AWebhookEvent>
    ): Boolean
    
    suspend fun handleWebhookEvent(event: A2AWebhookPayload)
}
```

### 2.3 Enhanced Error Handling

#### Retry Policies
Implement sophisticated retry mechanisms:

```kotlin
data class A2ARetryPolicy(
    val maxAttempts: Int = 3,
    val backoffStrategy: BackoffStrategy = ExponentialBackoff(),
    val retryableErrors: Set<Int> = setOf(-40001, -40002),
    val timeoutMs: Long = 30000
)
```

#### Circuit Breaker
Add circuit breaker pattern for resilient communication:

```kotlin
class A2ACircuitBreaker(
    private val failureThreshold: Int = 5,
    private val timeoutMs: Long = 60000
)
```

### 2.4 Phase 2 Deliverables

1. **Streaming Client**: SSE-based real-time communication
2. **Async Support**: Webhook configuration and handling
3. **Enhanced Error Handling**: Retry policies and circuit breakers
4. **Connection Management**: Connection pooling and keep-alive
5. **Monitoring**: Metrics and health checks

## Phase 3: A2A Server Implementation

### Scope
**Goal**: Full A2A server to expose Koog agents via A2A protocol

**Duration**: 5-6 weeks

### 3.1 A2A Server Infrastructure

#### New Module: `agents-a2a-server`
```
agents/
└── agents-a2a/
    └── agents-a2a-server/
        ├── Module.md
        ├── build.gradle.kts
        └── src/
            ├── commonMain/kotlin/ai/koog/agents/a2a/server/
            ├── jvmMain/kotlin/ai/koog/agents/a2a/server/
            └── commonTest/kotlin/ai/koog/agents/a2a/server/
```

#### A2A Server Core
Implement HTTP server with JSON-RPC endpoints:

```kotlin
class A2AServer(
    private val config: A2AServerConfig,
    private val agentRegistry: A2AAgentRegistry
) {
    suspend fun start()
    suspend fun stop()
    
    // JSON-RPC endpoint handlers
    suspend fun handleMessageSend(request: A2AMessageSendRequest): A2AResponse
    suspend fun handleMessageStream(request: A2AMessageStreamRequest): Flow<A2AStreamEvent>
    suspend fun handleTaskGet(request: A2ATaskGetRequest): A2ATask
    suspend fun handleTaskCancel(request: A2ATaskCancelRequest): Boolean
}
```

### 3.2 Agent Exposure

#### Agent Bridge
Bridge between Koog agents and A2A protocol:

```kotlin
class A2AAgentBridge(
    private val agent: AIAgent<*, *>,
    private val agentCard: A2AAgentCard
) {
    suspend fun executeSkill(
        skillName: String,
        parameters: Map<String, Any>
    ): A2AExecutionResult
    
    fun getAgentCard(): A2AAgentCard
}
```

#### Skill Mapping
Map agent tools to A2A skills:

```kotlin
class A2ASkillMapper {
    fun mapToolsToSkills(toolRegistry: ToolRegistry): List<A2ASkill>
    fun mapToolCallToSkillExecution(
        tool: Tool<*, *>,
        parameters: Map<String, Any>
    ): suspend () -> Any
}
```

### 3.3 Task Management

#### A2A Task Engine
Manage A2A tasks and their lifecycle:

```kotlin
class A2ATaskEngine {
    suspend fun createTask(message: A2AMessage): A2ATask
    suspend fun executeTask(taskId: String): A2AExecutionResult
    suspend fun getTask(taskId: String): A2ATask?
    suspend fun cancelTask(taskId: String): Boolean
    
    fun subscribeToTaskUpdates(taskId: String): Flow<A2ATaskUpdate>
}
```

### 3.4 Authentication and Security

#### A2A Authentication Abstractions

**IMPORTANT**: Authentication flow is performed out-of-band as per A2A specification. This implementation provides only the required abstractions.

##### Authentication Strategy (A2A Specification Compliant)
- **Transport-layer authentication**: Authentication occurs at HTTP level, not in JSON-RPC payloads (per A2A spec)
- **Out-of-band credential acquisition**: Clients obtain credentials through external processes before requests
- **Agent Card capability advertising**: Server advertises supported authentication in Agent Card via SecurityScheme
- **SecurityScheme definitions**: Server describes available authentication methods per A2A specification
- **Dynamic credentials**: Use short-lived, dynamic credentials rather than static secrets
- **HTTPS enforcement**: Always use HTTPS for production (A2A requirement)

```kotlin
// Authentication constants - no magic strings
object A2AAuthConstants {
    const val API_KEY_HEADER = "X-A2A-API-Key"
    const val BEARER_PREFIX = "Bearer "
    const val AUTH_HEADER = "Authorization"
    const val AGENT_ID_HEADER = "X-A2A-Agent-ID"
}

// A2A SecurityScheme types as per specification
object A2ASecuritySchemeTypes {
    const val OPENID_CONNECT = "openIdConnect"
    const val OAUTH2 = "oauth2"
    const val API_KEY = "apiKey"
    const val HTTP_BEARER = "http" // with scheme: bearer
    const val HTTP_BASIC = "http" // with scheme: basic
}

object A2AAuthSchemes {
    const val BEARER = "bearer"
    const val BASIC = "basic"
    const val API_KEY = "apiKey"
}

// Abstract provider that consumers must implement (A2A compliant)
abstract class A2AAuthenticationProvider {
    /**
     * Authenticate credentials extracted from HTTP transport layer.
     * Implementation provided by consumer.
     */
    abstract suspend fun authenticate(credentials: A2ACredentials): A2AAuthenticationResult
    
    /**
     * Authorize authenticated principal for action.
     * Implementation provided by consumer.
     */
    abstract suspend fun authorize(
        principal: A2APrincipal,
        resource: String,
        action: String
    ): Boolean
    
    /**
     * Return SecurityScheme definitions for Agent Card (A2A specification).
     */
    abstract fun getSecuritySchemes(): Map<String, A2ASecurityScheme>
    
    /**
     * Return required security scopes for Agent Card.
     */
    abstract fun getSecurityRequirements(): List<Map<String, List<String>>>
}

// Credential types extracted from HTTP transport (A2A specification)
sealed class A2ACredentials {
    data class ApiKey(val key: String, val keyName: String) : A2ACredentials()
    data class BearerToken(val token: String, val format: String? = null) : A2ACredentials()
    data class BasicAuth(val username: String, val password: String) : A2ACredentials()
    data class OAuth2Token(val accessToken: String, val scopes: Set<String>) : A2ACredentials()
    data class OpenIdConnect(val idToken: String, val claims: Map<String, Any>) : A2ACredentials()
}

// SecurityScheme definition as per A2A specification
@Serializable
data class A2ASecurityScheme(
    val type: String,
    val scheme: String? = null, // for http type
    val bearerFormat: String? = null, // for bearer tokens
    val openIdConnectUrl: String? = null, // for OpenID Connect
    val flows: A2AOAuthFlows? = null, // for OAuth 2.0
    val name: String? = null, // for apiKey
    val `in`: String? = null // for apiKey location (header, query, cookie)
)
```

### 3.5 Phase 3 Deliverables

1. **A2A Server**: Complete HTTP server with JSON-RPC endpoints
2. **Agent Bridge**: Expose Koog agents via A2A protocol
3. **Task Management**: Full task lifecycle management
4. **Authentication**: Multi-scheme authentication support
5. **Server Feature**: Installable feature for server capabilities

## Phase 4: Advanced Features

### Scope
**Goal**: Production-ready features and optimizations

**Duration**: 4-5 weeks

### 4.1 Production Features

#### Monitoring and Observability
```kotlin
class A2AMetrics {
    fun recordRequestDuration(method: String, duration: Duration)
    fun recordTaskCreated(agentId: String)
    fun recordTaskCompleted(agentId: String, success: Boolean)
    fun recordError(error: A2AError)
}
```

#### Health Checks
```kotlin
class A2AHealthCheck {
    suspend fun checkAgentHealth(agentId: String): HealthStatus
    suspend fun checkSystemHealth(): SystemHealthStatus
}
```

### 4.2 Advanced Communication

#### Multi-Agent Workflows
Support for complex multi-agent collaboration:

```kotlin
class A2AWorkflowEngine {
    suspend fun executeWorkflow(
        workflow: A2AWorkflow,
        participants: List<A2AAgentEndpoint>
    ): A2AWorkflowResult
}
```

#### Agent Discovery
Implement agent discovery mechanisms:

```kotlin
interface A2ADiscoveryService {
    suspend fun discoverAgents(criteria: A2ADiscoveryCriteria): List<A2AAgentCard>
    suspend fun registerAgent(agentCard: A2AAgentCard, endpoint: String)
    suspend fun unregisterAgent(agentId: String)
}
```

### 4.3 Performance Optimizations

#### Caching
Implement response caching:

```kotlin
class A2AResponseCache {
    suspend fun get(key: String): A2AResponse?
    suspend fun put(key: String, response: A2AResponse, ttl: Duration)
    suspend fun invalidate(pattern: String)
}
```

#### Connection Pooling
Optimize connection management:

```kotlin
class A2AConnectionPool {
    suspend fun getConnection(endpoint: String): A2AConnection
    suspend fun releaseConnection(connection: A2AConnection)
    fun configurePool(config: ConnectionPoolConfig)
}
```

### 4.4 Phase 4 Deliverables

1. **Production Monitoring**: Comprehensive metrics and health checks
2. **Discovery Service**: Agent discovery and registration
3. **Workflow Engine**: Multi-agent workflow orchestration
4. **Performance Features**: Caching and connection pooling
5. **Documentation**: Complete API documentation and deployment guides

## Implementation Timeline

| Phase | Duration | Key Deliverables | Dependencies |
|-------|----------|------------------|--------------|
| Phase 1 | 4-6 weeks | MVP Client, Remote Agent Tools | None |
| Phase 2 | 3-4 weeks | Streaming, Async, Enhanced Error Handling | Phase 1 |
| Phase 3 | 5-6 weeks | A2A Server, Agent Bridge | Phase 1-2 |
| Phase 4 | 4-5 weeks | Production Features, Advanced Communication | Phase 1-3 |
| **Total** | **16-19 weeks** | Complete A2A Integration | |

## Technical Considerations

### Dependencies

#### Dependencies Management

**IMPORTANT**: All dependencies must be managed through `gradle/libs.versions.toml`. Check the version catalog first before adding any dependency.

##### Required Dependencies (via version catalog)
1. **HTTP Client**: Use `libs.ktor.client.core` and related Ktor client libraries
2. **JSON-RPC**: Custom implementation using `libs.kotlinx.serialization.json`
3. **Server-Sent Events**: Use `libs.ktor.server.sse` and `libs.ktor.client.sse`
4. **Webhook Server**: Use `libs.ktor.server.core` and related server libraries
5. **JSON Schema**: Use existing serialization for validation

##### Testing Strategy
- **Use only Kotlin test**: `kotlin("test")` for common tests, `kotlin("test-junit5")` for JVM, `kotlin("test-js")` for JS
- **TestContainers only**: Use `libs.testcontainers.core` and `libs.testcontainers.junit.jupiter` for integration tests
- **NO other testing frameworks**: Do not add Kotest, Mockk, or other testing dependencies

##### Code Quality Guidelines
- **No magic constants**: All constants must be clearly defined with descriptive names
- **Named constant objects**: Group related constants in dedicated objects (e.g., `A2AJsonRpcMethods`, `A2AHttpPaths`)
- **Authentication abstractions**: Provide interfaces/abstract classes for out-of-band authentication implementation

#### Gradle Configuration
```kotlin
// In agents-a2a-client/build.gradle.kts
dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.serialization.json)
}

// In agents-a2a-server/build.gradle.kts
dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
}
```

### Multiplatform Considerations

#### Platform-Specific Implementations
- **JVM**: Full client and server capabilities
- **JS**: Client-only implementation with browser limitations
- **Native**: Basic client support where HTTP libraries available

#### Common Interface Design
Ensure all A2A interfaces can be implemented across platforms:

```kotlin
// Common interface
expect class A2AHttpClient {
    suspend fun post(url: String, body: String): A2AHttpResponse
    suspend fun postStream(url: String, body: String): Flow<String>
}

// JVM implementation using Ktor
actual class A2AHttpClient {
    private val client = HttpClient()
    // Implementation
}
```

### Testing Strategy

#### Unit Tests
- A2A message serialization/deserialization
- Remote agent tool execution
- Error handling and retry logic
- Authentication providers

#### Integration Tests
- End-to-end A2A communication
- Agent discovery and registration
- Multi-agent workflows
- Performance under load

#### Mock Implementations
```kotlin
class MockA2AServer : A2AServer {
    // Test doubles for unit testing
}

class A2ATestFixture {
    fun createMockAgent(): A2AAgentBridge
    fun createTestClient(): A2AClient
}
```

## Migration Path

### Existing Code Compatibility
The A2A integration is designed to be fully backward compatible:

1. **No Breaking Changes**: Existing agent code continues to work unchanged
2. **Opt-in Features**: A2A capabilities are added via features
3. **Gradual Adoption**: Teams can adopt A2A incrementally

### Migration Steps
1. **Phase 1**: Install A2A client feature on existing agents
2. **Phase 2**: Replace manual remote calls with A2A remote agent tools
3. **Phase 3**: Expose existing agents via A2A server
4. **Phase 4**: Migrate to A2A-based multi-agent workflows

### Example Migration
```kotlin
// Before: Manual remote agent call
val result = httpClient.post("/api/analyze", data)

// After: A2A remote agent tool
val analyzerTool = RemoteAgentTool<AnalysisRequest, AnalysisResult>(
    agentUrl = "https://analyzer-agent.example.com",
    skill = "analyze_text"
)
val result = analyzerTool.execute(AnalysisRequest(data))
```

## Success Metrics

### MVP Success Criteria
- [ ] Successfully call remote A2A-compliant agent from Koog agent
- [ ] Register and use at least 3 remote agent tools
- [ ] Handle authentication with remote agents
- [ ] Basic error handling and retry logic
- [ ] Documentation and examples complete

### Phase 2 Success Criteria
- [ ] Streaming communication with remote agents
- [ ] Async task handling with webhooks
- [ ] Advanced error handling with circuit breakers
- [ ] Connection pooling and performance optimization

### Phase 3 Success Criteria
- [ ] Expose Koog agent via A2A protocol
- [ ] Handle concurrent A2A requests
- [ ] Full task lifecycle management
- [ ] Multi-scheme authentication support

### Phase 4 Success Criteria
- [ ] Production-ready monitoring and health checks
- [ ] Agent discovery service operational
- [ ] Multi-agent workflow execution
- [ ] Performance benchmarks achieved

## Risk Assessment

### Technical Risks
1. **Protocol Complexity**: A2A specification complexity may require more time
   - *Mitigation*: Phased implementation with MVP focus
2. **Performance Impact**: Network calls may slow agent execution
   - *Mitigation*: Async execution and caching strategies
3. **Authentication Complexity**: Multiple auth schemes increase complexity
   - *Mitigation*: Start with simple API key authentication

### Integration Risks
1. **Breaking Changes**: Changes to core framework during integration
   - *Mitigation*: Close coordination with framework development
2. **Feature Conflicts**: A2A features may conflict with existing features
   - *Mitigation*: Comprehensive testing of feature combinations

### Operational Risks
1. **Network Dependencies**: Increased network dependencies for agent execution
   - *Mitigation*: Robust error handling and fallback mechanisms
2. **Security Vulnerabilities**: A2A server exposes attack surface
   - *Mitigation*: Security review and penetration testing

This comprehensive integration plan provides a structured approach to implementing A2A protocol support in the Koog Agents framework while maintaining the framework's design principles and ensuring backward compatibility.