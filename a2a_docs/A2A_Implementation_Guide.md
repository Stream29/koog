# A2A Implementation Guide - Updated for Latest Libraries

## Module Structure

### Client Module: `agents-a2a-client` (Kotlin Multiplatform)
Provides A2A client functionality for connecting FROM Koog agents TO other A2A-compliant agents.

**Supported Platforms**: JVM, JS
**Future Platforms**: Native targets (iOS, Android, macOS, Linux) - architecture designed for easy extension

### Server Module: `agents-a2a-server` (JVM Only)
Turns existing Koog agents INTO A2A-compliant servers that other agents can connect to.

**Supported Platforms**: JVM only (Ktor server limitations)

## Library Versions (2025)

### Core Dependencies

#### Ktor 3.1.0 (Latest - February 2025)
- **Client**: `io.ktor:ktor-client-core:3.1.0`
- **Server**: `io.ktor:ktor-server-netty:3.1.0`
- **SSE Support**: Built-in Server-Sent Events support for streaming
- **WebAssembly**: New wasm-js support for broader platform compatibility

#### Kotlinx Serialization 1.8.1 (Latest)
- **JSON**: `org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1`
- **Core**: `org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1`
- Compatible with Kotlin 2.1.20

#### Testing
- **TestContainers**: `1.19.6+` (Java base) with Kotest integration
- **Kotest Extensions**: `io.kotest.extensions:kotest-extensions-testcontainers:5.8.0`

## Project Structure

```
agents/
└── agents-a2a/
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
                
                // Ktor Client
                implementation("io.ktor:ktor-client-core:3.1.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.1.0")
                implementation("io.ktor:ktor-client-sse:3.1.0") // Server-Sent Events
                implementation("io.ktor:ktor-client-auth:3.1.0")
                implementation("io.ktor:ktor-client-logging:3.1.0")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                
                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                
                // UUID
                implementation("com.benasher44:uuid:0.8.4")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.ktor:ktor-client-mock:3.1.0")
            }
        }
        
        jvmMain {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.1.0") // CIO engine for JVM
                implementation("ch.qos.logback:logback-classic:1.4.14")
            }
        }
        
        jvmTest {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:5.8.0")
                implementation("io.kotest:kotest-extensions-testcontainers:5.8.0")
            }
        }
        
        jsMain {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.1.0")
            }
        }
        
        // Future Native support - keep for reference
        // nativeMain {
        //     dependencies {
        //         implementation("io.ktor:ktor-client-curl:3.1.0") // Curl engine for Native
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
    api(project(":agents:agents-a2a:agents-a2a-client"))
    
    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.1.0")
    implementation("io.ktor:ktor-server-netty:3.1.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.1.0")
    implementation("io.ktor:ktor-server-sse:3.1.0") // Server-Sent Events
    implementation("io.ktor:ktor-server-auth:3.1.0")
    implementation("io.ktor:ktor-server-cors:3.1.0")
    implementation("io.ktor:ktor-server-call-logging:3.1.0")
    implementation("io.ktor:ktor-server-status-pages:3.1.0")
    implementation("io.ktor:ktor-server-compression:3.1.0")
    
    // Serialization  
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // JSON-RPC (custom implementation)
    // Note: We'll need to implement this as there's no official Kotlin JSON-RPC library
    
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.ktor:ktor-server-test-host:3.1.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.1.0")
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
    testImplementation(project(":agents:agents-a2a:agents-a2a-client"))
    testImplementation(project(":agents:agents-a2a:agents-a2a-server"))
    testImplementation(project(":agents:agents-test"))
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest:kotest-extensions-testcontainers:5.8.0")
    
    // TestContainers
    testImplementation("org.testcontainers:testcontainers:1.19.6")
    testImplementation("org.testcontainers:junit-jupiter:1.19.6")
    
    // Ktor Client for integration tests
    testImplementation("io.ktor:ktor-client-cio:3.1.0")
    testImplementation("io.ktor:ktor-client-content-negotiation:3.1.0")
    
    // Logging
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.test {
    useJUnitPlatform()
}
```

## Core Implementation

### A2A Client (Multiplatform)

#### A2AClient Interface
```kotlin
// commonMain
interface A2AClient {
    suspend fun sendMessage(
        agentUrl: String,
        message: A2AMessage,
        auth: A2AAuthentication? = null
    ): A2AResponse
    
    suspend fun streamMessage(
        agentUrl: String,
        message: A2AMessage,
        auth: A2AAuthentication? = null
    ): Flow<A2AStreamEvent>
    
    suspend fun getTask(
        agentUrl: String,
        taskId: String,
        auth: A2AAuthentication? = null
    ): A2ATask
    
    suspend fun cancelTask(
        agentUrl: String,
        taskId: String,
        auth: A2AAuthentication? = null
    ): Boolean
    
    suspend fun getAgentCard(
        agentUrl: String,
        auth: A2AAuthentication? = null
    ): A2AAgentCard
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

#### A2A Server Core
```kotlin
// jvmMain
class A2AServer(
    private val config: A2AServerConfig,
    private val agentBridge: A2AAgentBridge
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
                    JsonRpcResponse(
                        error = JsonRpcError(-32603, cause.message ?: "Internal error"),
                        id = "unknown"
                    )
                )
            }
        }
        
        routing {
            post("/a2a") {
                val request = call.receive<JsonRpcRequest>()
                val response = handleJsonRpcRequest(request)
                call.respond(response)
            }
            
            sse("/a2a/stream") {
                val request = call.receive<JsonRpcRequest>()
                when (request.method) {
                    "message/stream" -> handleStreamRequest(request)
                    else -> send(ServerSentEvent(
                        data = Json.encodeToString(JsonRpcResponse(
                            error = JsonRpcError(-32601, "Method not found"),
                            id = request.id
                        ))
                    ))
                }
            }
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
a2a-python==0.2.0
fastapi==0.104.1
uvicorn==0.24.0
```

#### Simple Python A2A Test Agent
```python
# docker/test-a2a-agent/test_agent.py
from a2a_python import A2AServer, AgentCard, Skill
from fastapi import FastAPI
import asyncio

app = FastAPI()

# Create agent card
agent_card = AgentCard(
    name="Test Agent",
    description="A simple test agent for integration testing",
    version="1.0.0",
    capabilities=["text_processing"],
    skills=[
        Skill(
            name="echo",
            description="Echoes back the input text",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string"}
                },
                "required": ["text"]
            }
        ),
        Skill(
            name="reverse",
            description="Reverses the input text",
            parameters={
                "type": "object", 
                "properties": {
                    "text": {"type": "string"}
                },
                "required": ["text"]
            }
        )
    ]
)

# Initialize A2A server
a2a_server = A2AServer(agent_card=agent_card)

@a2a_server.skill("echo")
async def echo(text: str) -> str:
    return f"Echo: {text}"

@a2a_server.skill("reverse")
async def reverse(text: str) -> str:
    return text[::-1]

# Mount A2A endpoints
app.mount("/", a2a_server.app)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)
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

### Authentication
- API Key authentication (all platforms)
- Bearer token authentication (all platforms)  
- OAuth 2.0 (JVM only, due to redirect handling complexity)
- Custom authentication headers (all platforms)

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