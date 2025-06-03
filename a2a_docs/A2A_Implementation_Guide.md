# A2A Implementation Guide - Updated for Latest Libraries

## Reference Implementation

**IMPORTANT**: The official Google A2A Python SDK is included in `a2a_docs/a2a-python-sdk/` and should be used as a reference during implementation. This SDK provides the official Python implementation of the A2A protocol and demonstrates correct patterns for:

- JSON-RPC 2.0 request/response handling
- Server-Sent Events (SSE) streaming
- Agent card structure and validation
- Task lifecycle management
- Error handling patterns
- Security scheme implementation
- Push notification support

Key files to reference:
- `src/a2a/types.py` - Complete A2A type definitions (generated from official schema)
- `src/a2a/client/client.py` - Reference client implementation patterns
- `src/a2a/server/` - Server implementation architecture
- `tests/` - Comprehensive test patterns

## Module Structure

### Client Module: `agents-a2a-client` (Kotlin Multiplatform)
Provides A2A client functionality for connecting FROM Koog agents TO other A2A-compliant agents.

**Supported Platforms**: JVM, JS
**Future Platforms**: Native targets (iOS, Android, macOS, Linux) - architecture designed for easy extension

### Server Module: `agents-a2a-server` (JVM Only)
Turns existing Koog agents INTO A2A-compliant servers that other agents can connect to.

**Supported Platforms**: JVM only (Ktor server limitations)

## Library Versions (2025)

### Dependency Management Guidelines

**CRITICAL**: All dependencies must be managed through the version catalog in `gradle/libs.versions.toml`.

#### Before Adding Any Dependency:
1. **Check version catalog first**: Look in `gradle/libs.versions.toml` to see if the dependency already exists
2. **Use catalog reference**: If it exists, use the catalog reference (e.g., `libs.ktor.client.core`)
3. **Add to catalog**: If it doesn't exist, add it to the version catalog first, then reference it
4. **Never use hardcoded versions**: Always use the version catalog to ensure consistency

#### Core Dependencies

##### Ktor 3.1.0 (Latest - February 2025)
- **Client**: Use `libs.ktor.client.core` from version catalog
- **Server**: Use `libs.ktor.server.netty` from version catalog
- **SSE Support**: Built-in Server-Sent Events support for streaming
- **WebAssembly**: New wasm-js support for broader platform compatibility

##### Kotlinx Serialization 1.8.1 (Latest)
- **JSON**: Use `libs.kotlinx.serialization.json` from version catalog
- **Core**: Use `libs.kotlinx.serialization.core` from version catalog
- Compatible with Kotlin 2.1.20

##### Testing Dependencies
- **Kotlin Test**: Use `kotlin("test")` for common tests, `kotlin("test-junit5")` for JVM, `kotlin("test-js")` for JS
- **TestContainers**: Use `libs.testcontainers.core` and `libs.testcontainers.junit.jupiter` from version catalog
- **NO OTHER TESTING DEPENDENCIES**: Do not add Kotest, Mockk, or other testing frameworks aside from testcontainers-related ones

## Project Structure

```
agents/
└── agents-a2a/
    ├── agents-a2a-core/            # Common A2A definitions and interfaces
    │   ├── Module.md
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/ai/koog/agents/a2a/core/
    │       └── commonTest/kotlin/ai/koog/agents/a2a/core/
    ├── agents-a2a-client/          # KMP Client Module
    │   ├── Module.md
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── commonMain/kotlin/ai/koog/agents/a2a/client/
    │       ├── commonTest/kotlin/ai/koog/agents/a2a/client/
    │       ├── jvmMain/kotlin/ai/koog/agents/a2a/client/
    │       ├── jvmTest/kotlin/ai/koog/agents/a2a/client/
    │       ├── jsMain/kotlin/ai/koog/agents/a2a/client/
    │       └── jsTest/kotlin/ai/koog/agents/a2a/client/
    ├── agents-a2a-server/          # JVM Server Module
    │   ├── Module.md
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── jvmMain/kotlin/ai/koog/agents/a2a/server/
    │       └── jvmTest/kotlin/ai/koog/agents/a2a/server/
    └── agents-a2a-integration-tests/ # Integration Test Suite
        ├── Module.md
        ├── build.gradle.kts
        ├── docker/
        │   ├── test-a2a-agent/     # Python A2A test agent
        │   │   ├── Dockerfile
        │   │   ├── requirements.txt
        │   │   └── test_agent.py
        │   └── docker-compose.yml
        └── src/
            └── jvmTest/kotlin/ai/koog/agents/a2a/integration/
```

## Gradle Configuration

### Dependency Management

**IMPORTANT**: Before adding any dependencies to the A2A modules, always check `gradle/libs.versions.toml` first to see if the dependency already exists in the version catalog. If it exists, use the catalog reference. If it doesn't exist, add it to the version catalog and then reference it.

### Client Module (`agents-a2a-client/build.gradle.kts`)

```kotlin
plugins {
    id("ai.kotlin.multiplatform")
    id("ai.kotlin.configuration")
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvm()
    js(IR) {
        browser()
    }
    
    // Future Native targets - commented out for initial implementation
    // Uncomment when Native support is needed:
    // iosX64()
    // iosArm64()
    // macosX64()
    // macosArm64()
    // mingwX64()
    // linuxX64()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":agents:agents-core"))
                api(project(":agents:agents-tools"))
                api(project(":agents:agents-a2a:agents-a2a-core"))
                
                // Ktor Client - use version catalog
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.sse) // Server-Sent Events
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)
                
                // Serialization - use version catalog
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.core)
                
                // Coroutines - use version catalog
                implementation(libs.kotlinx.coroutines.core)
                
                // DateTime - use version catalog
                implementation(libs.kotlinx.datetime)
                
                // UUID - use version catalog
                implementation(libs.uuid)
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                // No additional testing dependencies should be added
                // Use only kotlin("test") for multiplatform testing
                implementation(libs.ktor.client.mock)
            }
        }
        
        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio) // CIO engine for JVM
                implementation(libs.logback.classic)
            }
        }
        
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
                // Only testcontainers-related testing dependencies are allowed
                implementation(libs.testcontainers.core)
                implementation(libs.testcontainers.junit.jupiter)
            }
        }
        
        jsMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        
        jsTest {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
        
        // Future Native support - keep for reference
        // nativeMain {
        //     dependencies {
        //         implementation(libs.ktor.client.curl) // Curl engine for Native
        //     }
        // }
    }
}
```

### Server Module (`agents-a2a-server/build.gradle.kts`)

```kotlin
plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.configuration")
    alias(libs.plugins.kotlinx.serialization)
}

dependencies {
    api(project(":agents:agents-core"))
    api(project(":agents:agents-tools"))
    api(project(":agents:agents-a2a:agents-a2a-core"))
    api(project(":agents:agents-a2a:agents-a2a-client"))
    
    // Ktor Server - use version catalog
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.sse) // Server-Sent Events
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.compression)
    
    // Serialization - use version catalog
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Coroutines - use version catalog
    implementation(libs.kotlinx.coroutines.core)
    
    // Logging - use version catalog
    implementation(libs.logback.classic)
    
    // JSON-RPC (custom implementation)
    // Note: We'll need to implement this as there's no official Kotlin JSON-RPC library
    
    testImplementation(kotlin("test-junit5"))
    // No additional testing dependencies should be added
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
}

tasks.test {
    useJUnitPlatform()
}
```

### Integration Tests Module (`agents-a2a-integration-tests/build.gradle.kts`)

```kotlin
plugins {
    id("ai.kotlin.jvm")
    id("ai.kotlin.configuration")
}

dependencies {
    testImplementation(project(":agents:agents-a2a:agents-a2a-core"))
    testImplementation(project(":agents:agents-a2a:agents-a2a-client"))
    testImplementation(project(":agents:agents-a2a:agents-a2a-server"))
    testImplementation(project(":agents:agents-test"))
    
    // Testing - only kotlin test and testcontainers allowed
    testImplementation(kotlin("test-junit5"))
    
    // TestContainers - use version catalog
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    
    // Ktor Client for integration tests - use version catalog
    testImplementation(libs.ktor.client.cio)
    testImplementation(libs.ktor.client.content.negotiation)
    
    // Logging - use version catalog
    testImplementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}
```

## Core Implementation

### A2A Client (Multiplatform)

**Reference**: Follow patterns from `a2a-python-sdk/src/a2a/client/client.py` for proper JSON-RPC 2.0 handling.

#### A2AClient Interface
```kotlin
// commonMain
interface A2AClient {
    suspend fun sendMessage(
        request: SendMessageRequest,
        httpKwargs: Map<String, Any>? = null
    ): SendMessageResponse
    
    suspend fun sendMessageStreaming(
        request: SendStreamingMessageRequest,
        httpKwargs: Map<String, Any>? = null
    ): Flow<SendStreamingMessageResponse>
    
    suspend fun getTask(
        request: GetTaskRequest,
        httpKwargs: Map<String, Any>? = null
    ): GetTaskResponse
    
    suspend fun cancelTask(
        request: CancelTaskRequest,
        httpKwargs: Map<String, Any>? = null
    ): CancelTaskResponse
    
    suspend fun getAgentCard(
        relativeCardPath: String? = null,
        httpKwargs: Map<String, Any>? = null
    ): AgentCard
    
    suspend fun setTaskCallback(
        request: SetTaskPushNotificationConfigRequest,
        httpKwargs: Map<String, Any>? = null
    ): SetTaskPushNotificationConfigResponse
    
    suspend fun getTaskCallback(
        request: GetTaskPushNotificationConfigRequest,
        httpKwargs: Map<String, Any>? = null
    ): GetTaskPushNotificationConfigResponse
}
```

#### Ktor-based Implementation
```kotlin
// commonMain
class KtorA2AClient(
    private val httpClient: HttpClient,
    private val config: A2AClientConfig
) : A2AClient {
    
    override suspend fun sendMessage(
        agentUrl: String,
        message: A2AMessage,
        auth: A2AAuthentication?
    ): A2AResponse {
        val request = JsonRpcRequest(
            method = "message/send",
            params = MessageSendParams(message),
            id = generateRequestId()
        )
        
        val response = httpClient.post("$agentUrl/a2a") {
            contentType(ContentType.Application.Json)
            setBody(request)
            auth?.let { setAuth(it) }
        }
        
        return response.body<JsonRpcResponse>().let { 
            Json.decodeFromJsonElement<A2AResponse>(it.result!!)
        }
    }
    
    override suspend fun streamMessage(
        agentUrl: String,
        message: A2AMessage,
        auth: A2AAuthentication?
    ): Flow<A2AStreamEvent> = flow {
        httpClient.prepareSse("$agentUrl/a2a/stream") {
            setBody(JsonRpcRequest(
                method = "message/stream",
                params = MessageStreamParams(message),
                id = generateRequestId()
            ))
            auth?.let { setAuth(it) }
        }.execute { sseSession ->
            sseSession.incoming.collect { serverSentEvent ->
                val event = Json.decodeFromString<A2AStreamEvent>(serverSentEvent.data ?: "")
                emit(event)
            }
        }
    }
}
```

### A2A Server (JVM Only)

**Reference**: Follow architecture patterns from `a2a-python-sdk/src/a2a/server/` for proper layered design.

#### Core Server Architecture
The Python SDK demonstrates a clean separation of concerns:
- **Request Handlers**: Handle JSON-RPC routing (`request_handlers/`)
- **Agent Execution**: Business logic execution (`agent_execution/`)
- **Task Management**: Task lifecycle management (`tasks/`)
- **Event System**: Event publishing and consumption (`events/`)

#### A2A Server Core
```kotlin
// jvmMain
class A2AServer(
    private val config: A2AServerConfig,
    private val agentExecutor: AgentExecutor,
    private val taskManager: TaskManager,
    private val eventQueueManager: EventQueueManager
) {
    private val server = embeddedServer(Netty, port = config.port) {
        install(ContentNegotiation) {
            json()
        }
        install(SSE)
        install(CORS) {
            anyHost()
        }
        install(CallLogging)
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(
                    HttpStatusCode.InternalServerError,
                    JSONRPCErrorResponse(
                        error = InternalError(
                            message = cause.message ?: "Internal error"
                        ),
                        id = "unknown"
                    )
                )
            }
        }
        
        routing {
            // Agent card endpoint (/.well-known/agent.json)
            get("/.well-known/agent.json") {
                call.respond(agentExecutor.getAgentCard())
            }
            
            // Main JSON-RPC endpoint
            post("/") {
                val request = call.receive<JSONRPCRequest>()
                val response = handleJsonRpcRequest(request)
                call.respond(response)
            }
            
            // Streaming endpoint for SSE
            sse("/stream") {
                val request = receiveMessageFromBody<SendStreamingMessageRequest>()
                handleStreamingRequest(request)
            }
        }
    }
    
    private suspend fun handleJsonRpcRequest(request: JSONRPCRequest): JSONRPCResponse {
        return when (request.method) {
            "message/send" -> handleSendMessage(request)
            "tasks/get" -> handleGetTask(request)
            "tasks/cancel" -> handleCancelTask(request)
            "tasks/pushNotificationConfig/set" -> handleSetPushNotificationConfig(request)
            "tasks/pushNotificationConfig/get" -> handleGetPushNotificationConfig(request)
            "tasks/resubscribe" -> handleTaskResubscription(request)
            else -> JSONRPCErrorResponse(
                error = MethodNotFoundError(),
                id = request.id
            )
        }
    }
    
    suspend fun start() = server.start(wait = false)
    suspend fun stop() = server.stop()
}
```

## Integration Testing Strategy

### TestContainers Setup

#### Python A2A Test Agent Docker Image
```dockerfile
# docker/test-a2a-agent/Dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY test_agent.py .

EXPOSE 8080

CMD ["python", "test_agent.py"]
```

#### Requirements for Test Agent
```txt
# docker/test-a2a-agent/requirements.txt
a2a-sdk>=1.0.0
starlette>=0.36.0
uvicorn[standard]>=0.24.0
```

#### Simple Python A2A Test Agent
```python
# docker/test-a2a-agent/test_agent.py
"""
Simple A2A test agent using the official Google A2A Python SDK.
Reference: a2a-python-sdk examples for proper implementation patterns.
"""
import asyncio
from typing import Any

from a2a.server.agent_execution.agent_executor import AgentExecutor
from a2a.server.agent_execution.context import RequestContext
from a2a.server.events.event_queue import EventQueue
from a2a.server.apps.starlette_app import create_starlette_app
from a2a.types import (
    AgentCard, AgentSkill, AgentProvider, AgentCapabilities,
    Message, TextPart, Task, TaskStatus, TaskState
)
import uvicorn

class TestAgentExecutor(AgentExecutor):
    """Simple test agent that echoes and reverses text."""
    
    def __init__(self):
        self.agent_card = AgentCard(
            name="Test Agent",
            description="A simple test agent for integration testing",
            version="1.0.0",
            url="http://localhost:8080",
            provider=AgentProvider(
                organization="Koog Test",
                url="https://github.com/koog/agents"
            ),
            capabilities=AgentCapabilities(
                streaming=True,
                pushNotifications=False,
                stateTransitionHistory=True
            ),
            defaultInputModes=["text/plain"],
            defaultOutputModes=["text/plain"],
            skills=[
                AgentSkill(
                    id="echo",
                    name="Echo",
                    description="Echoes back the input text",
                    tags=["text", "utility"],
                    examples=["Echo 'hello world'"]
                ),
                AgentSkill(
                    id="reverse",
                    name="Reverse",
                    description="Reverses the input text",
                    tags=["text", "utility"],
                    examples=["Reverse 'hello world'"]
                )
            ]
        )
    
    async def execute(self, context: RequestContext, event_queue: EventQueue):
        """Execute agent logic based on incoming message."""
        message = context.message
        
        # Extract text from message parts
        text_content = ""
        for part in message.parts:
            if hasattr(part, 'text'):
                text_content += part.text
        
        # Simple command processing
        if "echo" in text_content.lower():
            result_text = f"Echo: {text_content}"
        elif "reverse" in text_content.lower():
            # Remove the "reverse" command and reverse the rest
            clean_text = text_content.lower().replace("reverse", "").strip()
            result_text = clean_text[::-1]
        else:
            result_text = f"I received: {text_content}. Try 'echo <text>' or 'reverse <text>'"
        
        # Create response message
        response_message = Message(
            messageId=f"response-{context.message.messageId}",
            role="agent",
            parts=[TextPart(text=result_text)],
            taskId=context.task_id
        )
        
        # Update task status to completed
        task_status = TaskStatus(
            state=TaskState.completed,
            message=response_message
        )
        
        task = Task(
            id=context.task_id,
            contextId=context.context_id,
            status=task_status,
            history=[context.message, response_message]
        )
        
        # Publish the completed task
        await event_queue.put(task)
    
    async def cancel(self, context: RequestContext, event_queue: EventQueue):
        """Handle task cancellation."""
        # For this simple agent, just mark as canceled
        task_status = TaskStatus(state=TaskState.canceled)
        task = Task(
            id=context.task_id,
            contextId=context.context_id,
            status=task_status
        )
        await event_queue.put(task)
    
    def get_agent_card(self) -> AgentCard:
        """Return the agent card."""
        return self.agent_card

# Create and run the A2A server
async def main():
    agent_executor = TestAgentExecutor()
    app = create_starlette_app(agent_executor)
    
    config = uvicorn.Config(app, host="0.0.0.0", port=8080, log_level="info")
    server = uvicorn.Server(config)
    await server.serve()

if __name__ == "__main__":
    asyncio.run(main())
```

### Integration Test Implementation

#### Client Integration Tests
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2AClientIntegrationTest.kt

class A2AClientIntegrationTest : FunSpec({
    
    val testAgentContainer = GenericContainer<Nothing>("test-a2a-agent:latest")
        .withExposedPorts(8080)
        .withStartupTimeout(Duration.ofMinutes(2))
    
    beforeSpec {
        testAgentContainer.start()
    }
    
    afterSpec {
        testAgentContainer.stop()
    }
    
    test("should connect to A2A agent and call echo skill") {
        // Given
        val client = KtorA2AClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json()
                }
            },
            config = A2AClientConfig()
        )
        
        val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
        
        // When
        val message = A2AMessage(
            parts = listOf(
                A2AMessagePart.StructuredData(
                    data = mapOf("text" to "Hello, A2A!")
                )
            )
        )
        
        val response = client.sendMessage(agentUrl, message)
        
        // Then
        response shouldNotBe null
        // Additional assertions based on expected response
    }
    
    test("should stream messages from A2A agent") {
        // Similar setup but testing streaming functionality
    }
    
    test("should handle authentication with A2A agent") {
        // Test various authentication schemes
    }
})
```

#### Server Integration Tests
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2AServerIntegrationTest.kt

class A2AServerIntegrationTest : FunSpec({
    
    test("should expose Koog agent as A2A server") {
        // Given
        val toolRegistry = ToolRegistry.builder()
            .register(EchoTool)
            .register(ReverseTool)
            .build()
            
        val agent = AIAgent.builder()
            .strategy(SimpleStrategy())
            .toolRegistry(toolRegistry)
            .build()
            
        val agentBridge = A2AAgentBridge(agent)
        val server = A2AServer(
            config = A2AServerConfig(port = 0), // Random port
            agentBridge = agentBridge
        )
        
        server.start()
        val serverPort = server.getPort()
        
        try {
            // When - Use Python A2A client to test our server
            val pythonClientContainer = GenericContainer<Nothing>("python:3.11-slim")
                .withCommand("python", "-c", """
                    import asyncio
                    from a2a_python import A2AClient
                    
                    async def test():
                        client = A2AClient()
                        response = await client.send_message(
                            'http://host.docker.internal:$serverPort',
                            {'text': 'Hello from Python!'}
                        )
                        print(response)
                    
                    asyncio.run(test())
                """.trimIndent())
                .withNetworkMode("host")
                
            pythonClientContainer.start()
            val logs = pythonClientContainer.logs
            
            // Then
            logs should contain("Echo: Hello from Python!")
            
        } finally {
            server.stop()
        }
    }
})
```

#### Docker Compose for Complex Tests
```yaml
# docker/docker-compose.yml
version: '3.8'
services:
  test-a2a-agent:
    build: ./test-a2a-agent
    ports:
      - "8080:8080"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      
  koog-a2a-server:
    build: 
      context: ../..
      dockerfile: docker/koog-server/Dockerfile
    ports:
      - "8081:8081"
    depends_on:
      test-a2a-agent:
        condition: service_healthy
```

## Platform-Specific Considerations

### JVM Implementation (Primary Target)
- **Full Support**: Both client and server capabilities
- **Engine**: CIO engine for optimal performance and connection pooling
- **Features**: Complete SSE support, all authentication schemes, TestContainers integration
- **Server**: Full A2A server implementation with Ktor server
- **Performance**: Production-ready with monitoring and metrics

### JavaScript Implementation (Secondary Target)
- **Client-Only**: A2A client functionality (no server due to JS runtime limitations)
- **Engine**: JS engine with Fetch API integration  
- **Features**: SSE support for streaming, basic authentication (API key, bearer token)
- **Browser**: Full browser support with CORS handling
- **Target**: Browser environment only (consistent with other Koog modules)

### Future Native Support (Architecture Ready)
The architecture is designed to easily add Native support in the future:
- **Platforms**: iOS, Android, macOS, Linux, Windows
- **Engine**: Curl engine for HTTP client
- **Limitations**: Client-only, platform-dependent SSL/TLS
- **Migration**: Simply uncomment Native targets in gradle configuration

```kotlin
// Future Native enablement - just uncomment:
// iosX64()
// iosArm64() 
// macosX64()
// macosArm64()
// linuxX64()
// mingwX64()
```

## Security Considerations

### Authentication Strategy

**IMPORTANT**: Authentication is performed out-of-band at the HTTP transport layer as per A2A specification. This implementation provides only the required abstractions.

#### A2A Authentication Requirements (per specification)
- **Transport-layer authentication**: Authentication occurs at HTTP level, not in JSON-RPC payloads
- **Out-of-band credential acquisition**: Clients obtain credentials through external processes before making requests
- **Agent Card capability advertising**: Server advertises supported authentication methods in Agent Card
- **SecurityScheme definitions**: Server describes available authentication methods via SecurityScheme objects
- **HTTPS enforcement**: Always use HTTPS for production deployments
- **Dynamic credentials**: Use short-lived, dynamic credentials rather than static secrets

#### Supported Authentication Schemes (A2A Standard)
- **OpenID Connect**: Enterprise-grade authentication with identity providers
- **OAuth 2.0**: Standard OAuth 2.0 flows for secure token-based authentication
- **Bearer Token**: RFC 6750 compliant bearer token authentication
- **API Key**: Simple API key authentication via HTTP headers
- **Basic Authentication**: HTTP Basic authentication (RFC 7617)
- **Custom**: Custom authentication schemes via HTTP headers

```kotlin
// Authentication constants - aligned with A2A specification
object A2AAuthConstants {
    const val API_KEY_HEADER = "X-API-Key"
    const val BEARER_PREFIX = "Bearer "
    const val BASIC_PREFIX = "Basic "
    const val AUTH_HEADER = "Authorization"
    const val AGENT_ID_HEADER = "X-Agent-ID"
}

// A2A SecurityScheme types as per specification
object A2ASecuritySchemeTypes {
    const val OPENID_CONNECT = "openIdConnect"
    const val OAUTH2 = "oauth2"
    const val API_KEY = "apiKey"
    const val HTTP_BEARER = "http"
    const val HTTP_BASIC = "http"
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

@Serializable
data class A2AOAuthFlows(
    val authorizationCode: A2AOAuthFlow? = null,
    val implicit: A2AOAuthFlow? = null,
    val password: A2AOAuthFlow? = null,
    val clientCredentials: A2AOAuthFlow? = null
)

@Serializable
data class A2AOAuthFlow(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String> = emptyMap()
)

// Abstract provider that consumers implement
abstract class A2AAuthenticationProvider {
    /**
     * Authenticate credentials extracted from HTTP transport layer.
     * Implementation provided by consumer - called after credential extraction.
     */
    abstract suspend fun authenticate(credentials: A2ACredentials): A2AAuthenticationResult
    
    /**
     * Authorize authenticated principal for specific action.
     * Implementation provided by consumer.
     */
    abstract suspend fun authorize(
        principal: A2APrincipal,
        resource: String,
        action: String
    ): Boolean
    
    /**
     * Return SecurityScheme definitions for Agent Card.
     * Must align with A2A specification SecurityScheme format.
     */
    abstract fun getSecuritySchemes(): Map<String, A2ASecurityScheme>
    
    /**
     * Return required security scopes for Agent Card.
     */
    abstract fun getSecurityRequirements(): List<Map<String, List<String>>>
}

// Credential types extracted from HTTP transport
sealed class A2ACredentials {
    data class ApiKey(val key: String, val keyName: String) : A2ACredentials()
    data class BearerToken(val token: String, val format: String? = null) : A2ACredentials()
    data class BasicAuth(val username: String, val password: String) : A2ACredentials()
    data class OAuth2Token(val accessToken: String, val scopes: Set<String>) : A2ACredentials()
    data class OpenIdConnect(val idToken: String, val claims: Map<String, Any>) : A2ACredentials()
}
```

### Transport Security
- HTTPS enforcement
- Certificate validation
- Request signing (JVM only)
- Rate limiting (server-side)

## Performance Optimization

### Connection Management
- HTTP/2 support where available
- Connection pooling
- Keep-alive connections
- Compression (gzip/deflate)

### Caching
- Agent card caching
- Response caching for idempotent operations  
- Connection metadata caching

### Monitoring
- Request/response metrics
- Error rate tracking
- Performance timing
- Health check endpoints

This implementation guide provides a solid foundation for building A2A protocol support in the Koog Agents framework with modern libraries and comprehensive testing.