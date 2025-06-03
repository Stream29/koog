# A2A Testing Strategy with TestContainers

## Overview

This document outlines a comprehensive testing strategy for the A2A integration in Koog Agents framework, using TestContainers for integration testing with real A2A-compliant agents.

## Testing Architecture

```
Testing Layers:
┌─────────────────────────────────────────────────────────────┐
│                    E2E Tests                                │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │  Koog Client    │◄──►│  Python A2A     │                │
│  │  Agent          │    │  Test Server    │                │
│  └─────────────────┘    └─────────────────┘                │
├─────────────────────────────────────────────────────────────┤
│                Integration Tests                            │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │  Koog A2A       │◄──►│  Mock A2A       │                │
│  │  Server         │    │  Client         │                │
│  └─────────────────┘    └─────────────────┘                │
├─────────────────────────────────────────────────────────────┤
│                  Unit Tests                                 │
│  ┌─────────────────┐    ┌─────────────────┐                │
│  │  A2A Client     │    │  A2A Server     │                │
│  │  Components     │    │  Components     │                │
│  └─────────────────┘    └─────────────────┘                │
└─────────────────────────────────────────────────────────────┘
```

## Test Docker Images

### Python A2A Test Agent Image

#### Dockerfile
```dockerfile
# docker/test-a2a-agent/Dockerfile
FROM python:3.11-slim

# Install system dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY test_agent.py .
COPY health_check.py .

# Create non-root user
RUN useradd -m -u 1000 agent && chown -R agent:agent /app
USER agent

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD python health_check.py

EXPOSE 8080

CMD ["python", "test_agent.py"]
```

#### Requirements
```txt
# docker/test-a2a-agent/requirements.txt
a2a-python==0.2.0
fastapi==0.104.1
uvicorn[standard]==0.24.0
pydantic==2.5.0
httpx==0.25.2
```

#### Test Agent Implementation
```python
# docker/test-a2a-agent/test_agent.py
import asyncio
import json
import logging
from typing import Dict, Any, List
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import uvicorn
from a2a_python import A2AServer, AgentCard, Skill, Message, Task
from pydantic import BaseModel

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class EchoRequest(BaseModel):
    text: str

class ReverseRequest(BaseModel):
    text: str

class MathRequest(BaseModel):
    operation: str  # "add", "multiply", "divide"
    a: float
    b: float

class DelayRequest(BaseModel):
    text: str
    delay_seconds: int = 5

# Agent configuration
AGENT_CARD = AgentCard(
    name="Koog Test Agent",
    description="A comprehensive test agent for Koog A2A integration testing",
    version="1.0.0",
    capabilities=[
        "text_processing", 
        "mathematical_operations",
        "streaming_responses",
        "async_processing"
    ],
    skills=[
        Skill(
            name="echo",
            description="Echoes back the input text with timestamp",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text to echo back"}
                },
                "required": ["text"]
            },
            returns={
                "type": "object",
                "properties": {
                    "response": {"type": "string"},
                    "timestamp": {"type": "string"},
                    "agent": {"type": "string"}
                }
            }
        ),
        Skill(
            name="reverse",
            description="Reverses the input text",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string", "description": "Text to reverse"}
                },
                "required": ["text"]
            }
        ),
        Skill(
            name="math",
            description="Performs mathematical operations",
            parameters={
                "type": "object",
                "properties": {
                    "operation": {
                        "type": "string", 
                        "enum": ["add", "multiply", "divide", "subtract"],
                        "description": "Mathematical operation to perform"
                    },
                    "a": {"type": "number", "description": "First operand"},
                    "b": {"type": "number", "description": "Second operand"}
                },
                "required": ["operation", "a", "b"]
            }
        ),
        Skill(
            name="delayed_processing",
            description="Processes text with configurable delay (for async testing)",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string"},
                    "delay_seconds": {"type": "integer", "minimum": 1, "maximum": 30}
                },
                "required": ["text"]
            }
        ),
        Skill(
            name="error_simulation",
            description="Simulates different types of errors for error handling testing",
            parameters={
                "type": "object",
                "properties": {
                    "error_type": {
                        "type": "string",
                        "enum": ["timeout", "invalid_input", "server_error", "auth_error"],
                        "description": "Type of error to simulate"
                    }
                },
                "required": ["error_type"]
            }
        )
    ]
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting Koog Test A2A Agent")
    yield
    logger.info("Shutting down Koog Test A2A Agent")

# Initialize FastAPI app
app = FastAPI(
    title="Koog Test A2A Agent",
    description="Test agent for Koog A2A integration testing",
    version="1.0.0",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize A2A server
a2a_server = A2AServer(agent_card=AGENT_CARD)

# Skill implementations
@a2a_server.skill("echo")
async def echo_skill(text: str) -> Dict[str, Any]:
    import datetime
    logger.info(f"Echo skill called with text: {text}")
    return {
        "response": f"Echo: {text}",
        "timestamp": datetime.datetime.utcnow().isoformat(),
        "agent": "Koog Test Agent"
    }

@a2a_server.skill("reverse")
async def reverse_skill(text: str) -> str:
    logger.info(f"Reverse skill called with text: {text}")
    return text[::-1]

@a2a_server.skill("math")
async def math_skill(operation: str, a: float, b: float) -> Dict[str, Any]:
    logger.info(f"Math skill called: {operation}({a}, {b})")
    
    result = None
    if operation == "add":
        result = a + b
    elif operation == "subtract":
        result = a - b
    elif operation == "multiply":
        result = a * b
    elif operation == "divide":
        if b == 0:
            raise ValueError("Division by zero")
        result = a / b
    else:
        raise ValueError(f"Unknown operation: {operation}")
    
    return {
        "operation": operation,
        "operands": [a, b],
        "result": result
    }

@a2a_server.skill("delayed_processing")
async def delayed_processing_skill(text: str, delay_seconds: int = 5) -> Dict[str, Any]:
    logger.info(f"Delayed processing skill called with {delay_seconds}s delay")
    await asyncio.sleep(delay_seconds)
    return {
        "processed_text": text.upper(),
        "delay_applied": delay_seconds,
        "status": "completed"
    }

@a2a_server.skill("error_simulation") 
async def error_simulation_skill(error_type: str) -> Dict[str, Any]:
    logger.info(f"Error simulation skill called with type: {error_type}")
    
    if error_type == "timeout":
        await asyncio.sleep(60)  # Simulate timeout
        return {"status": "timeout_completed"}
    elif error_type == "invalid_input":
        raise ValueError("Simulated invalid input error")
    elif error_type == "server_error":
        raise RuntimeError("Simulated server error")
    elif error_type == "auth_error":
        raise PermissionError("Simulated authentication error")
    else:
        raise ValueError(f"Unknown error type: {error_type}")

# Health check endpoint
@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "agent": AGENT_CARD.name,
        "version": AGENT_CARD.version,
        "timestamp": str(asyncio.get_event_loop().time())
    }

# Mount A2A endpoints
app.mount("/a2a", a2a_server.app)

if __name__ == "__main__":
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8080,
        access_log=True,
        log_level="info"
    )
```

#### Health Check Script
```python
# docker/test-a2a-agent/health_check.py
import sys
import httpx
import asyncio

async def check_health():
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get("http://localhost:8080/health", timeout=5.0)
            if response.status_code == 200:
                print("Health check passed")
                return True
            else:
                print(f"Health check failed with status {response.status_code}")
                return False
    except Exception as e:
        print(f"Health check failed with error: {e}")
        return False

if __name__ == "__main__":
    result = asyncio.run(check_health())
    sys.exit(0 if result else 1)
```

### Advanced Test Agent (Multi-Agent Scenario)
```python
# docker/multi-agent-test/coordinator_agent.py
"""
Coordinator agent that delegates tasks to other agents
"""
from a2a_python import A2AServer, A2AClient, AgentCard, Skill

COORDINATOR_CARD = AgentCard(
    name="Task Coordinator",
    description="Coordinates tasks between multiple agents",
    version="1.0.0",
    capabilities=["task_coordination", "agent_discovery", "workflow_management"],
    skills=[
        Skill(
            name="coordinate_text_processing",
            description="Coordinates text processing across multiple agents",
            parameters={
                "type": "object",
                "properties": {
                    "text": {"type": "string"},
                    "operations": {
                        "type": "array",
                        "items": {"type": "string", "enum": ["echo", "reverse", "uppercase"]}
                    }
                },
                "required": ["text", "operations"]
            }
        )
    ]
)

coordinator = A2AServer(agent_card=COORDINATOR_CARD)

@coordinator.skill("coordinate_text_processing")
async def coordinate_text_processing(text: str, operations: list) -> dict:
    client = A2AClient()
    results = {}
    
    for operation in operations:
        if operation == "echo":
            response = await client.send_message(
                "http://test-agent:8080/a2a",
                {"skill": "echo", "text": text}
            )
        elif operation == "reverse":
            response = await client.send_message(
                "http://test-agent:8080/a2a", 
                {"skill": "reverse", "text": text}
            )
        # Add more operations as needed
        
        results[operation] = response
    
    return {"coordinated_results": results, "original_text": text}
```

## TestContainers Integration

### Base Test Configuration
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2ATestConfig.kt

object A2ATestConfig {
    const val TEST_AGENT_IMAGE = "koog-test-a2a-agent:latest"
    const val COORDINATOR_AGENT_IMAGE = "koog-coordinator-agent:latest"
    const val TEST_NETWORK = "a2a-test-network"
    
    fun createTestNetwork(): Network {
        return NetworkImpl.builder()
            .name(TEST_NETWORK)
            .checkDuplicate(false)
            .build()
    }
    
    fun createTestAgentContainer(network: Network): GenericContainer<Nothing> {
        return GenericContainer<Nothing>(TEST_AGENT_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("test-agent")
            .withExposedPorts(8080)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLogConsumer { frame ->
                print("[TEST-AGENT] ${frame.utf8String}")
            }
            .waitingFor(
                Wait.forHttp("/health")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2))
            )
    }
}
```

### Client Integration Tests
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2AClientIntegrationTest.kt

class A2AClientIntegrationTest : FunSpec({
    
    lateinit var network: Network
    lateinit var testAgentContainer: GenericContainer<Nothing>
    lateinit var client: A2AClient
    
    beforeSpec {
        network = A2ATestConfig.createTestNetwork()
        testAgentContainer = A2ATestConfig.createTestAgentContainer(network)
        testAgentContainer.start()
        
        client = KtorA2AClient(
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json()
                }
                install(Logging) {
                    level = LogLevel.INFO
                }
            },
            config = A2AClientConfig()
        )
    }
    
    afterSpec {
        testAgentContainer.stop()
        network.close()
    }
    
    context("Basic A2A Communication") {
        test("should successfully connect to test agent") {
            // Given
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            
            // When
            val agentCard = client.getAgentCard(agentUrl)
            
            // Then
            agentCard.name shouldBe "Koog Test Agent"
            agentCard.capabilities should contain("text_processing")
            agentCard.skills.map { it.name } should containAll(
                listOf("echo", "reverse", "math")
            )
        }
        
        test("should execute echo skill successfully") {
            // Given
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            val message = A2AMessage(
                parts = listOf(
                    A2AMessagePart.StructuredData(
                        data = mapOf(
                            "skill" to "echo",
                            "text" to "Hello from Koog!"
                        )
                    )
                )
            )
            
            // When
            val response = client.sendMessage(agentUrl, message)
            
            // Then
            response.task.status shouldBe A2ATaskStatus.COMPLETED
            val result = response.artifacts.first().content as Map<String, Any>
            result["response"] shouldBe "Echo: Hello from Koog!"
            result["agent"] shouldBe "Koog Test Agent"
        }
        
        test("should handle mathematical operations") {
            // Given
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            val message = A2AMessage(
                parts = listOf(
                    A2AMessagePart.StructuredData(
                        data = mapOf(
                            "skill" to "math",
                            "operation" to "add",
                            "a" to 15.5,
                            "b" to 24.5
                        )
                    )
                )
            )
            
            // When
            val response = client.sendMessage(agentUrl, message)
            
            // Then
            response.task.status shouldBe A2ATaskStatus.COMPLETED
            val result = response.artifacts.first().content as Map<String, Any>
            result["result"] shouldBe 40.0
            result["operation"] shouldBe "add"
        }
    }
    
    context("Streaming Communication") {
        test("should handle streaming responses") {
            // Given
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            val message = A2AMessage(
                parts = listOf(
                    A2AMessagePart.StructuredData(
                        data = mapOf(
                            "skill" to "delayed_processing",
                            "text" to "Stream test",
                            "delay_seconds" to 2
                        )
                    )
                )
            )
            
            // When
            val events = mutableListOf<A2AStreamEvent>()
            client.streamMessage(agentUrl, message).collect { event ->
                events.add(event)
            }
            
            // Then
            events should haveAtLeastSize(2)
            events.last() shouldBe A2AStreamEvent.StreamComplete
            events.filterIsInstance<A2AStreamEvent.TaskUpdate>()
                .any { it.task.status == A2ATaskStatus.COMPLETED } shouldBe true
        }
    }
    
    context("Error Handling") {
        test("should handle agent errors gracefully") {
            // Given
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            val message = A2AMessage(
                parts = listOf(
                    A2AMessagePart.StructuredData(
                        data = mapOf(
                            "skill" to "error_simulation",
                            "error_type" to "invalid_input"
                        )
                    )
                )
            )
            
            // When & Then
            shouldThrow<A2AException> {
                client.sendMessage(agentUrl, message)
            }.let { exception ->
                exception.code shouldBe -40003 // Capability mismatch or processing error
            }
        }
        
        test("should handle network timeouts") {
            // Given
            val agentUrl = "http://non-existent-agent:8080"
            val message = A2AMessage(
                parts = listOf(
                    A2AMessagePart.Text("test")
                )
            )
            
            // When & Then
            shouldThrow<A2ANetworkException> {
                client.sendMessage(agentUrl, message)
            }
        }
    }
    
    context("Authentication") {
        test("should handle API key authentication") {
            // Test API key auth when implemented
        }
        
        test("should handle bearer token authentication") {
            // Test bearer token auth when implemented  
        }
    }
})
```

### Server Integration Tests
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2AServerIntegrationTest.kt

class A2AServerIntegrationTest : FunSpec({
    
    lateinit var network: Network
    lateinit var pythonClientContainer: GenericContainer<Nothing>
    lateinit var koogServer: A2AServer
    lateinit var serverPort: Int
    
    beforeSpec {
        network = A2ATestConfig.createTestNetwork()
        
        // Set up Koog agent with A2A server
        val toolRegistry = ToolRegistry.builder()
            .register(EchoTool)
            .register(ReverseTool)
            .register(CalculatorTool)
            .build()
            
        val agent = AIAgent.builder()
            .strategy(SimpleStrategy())
            .toolRegistry(toolRegistry)
            .build()
            
        val agentBridge = A2AAgentBridge(agent)
        koogServer = A2AServer(
            config = A2AServerConfig(port = 0), // Random port
            agentBridge = agentBridge
        )
        
        koogServer.start()
        serverPort = koogServer.getPort()
        
        // Set up Python client container to test our server
        pythonClientContainer = GenericContainer<Nothing>("python:3.11-slim")
            .withNetwork(network)
            .withNetworkAliases("python-client")
            .withFileSystemBind(
                "${System.getProperty("user.dir")}/docker/python-client",
                "/app",
                BindMode.READ_ONLY
            )
            .withWorkingDirectory("/app")
            .withCommand("tail", "-f", "/dev/null") // Keep container running
            .withStartupTimeout(Duration.ofMinutes(2))
            
        pythonClientContainer.start()
        
        // Install dependencies in Python container
        pythonClientContainer.execInContainer("pip", "install", "a2a-python", "httpx")
    }
    
    afterSpec {
        koogServer.stop()
        pythonClientContainer.stop()
        network.close()
    }
    
    context("Koog Agent as A2A Server") {
        test("should expose Koog agent capabilities via A2A") {
            // Given
            val testScript = """
                import asyncio
                import json
                from a2a_python import A2AClient
                
                async def test_agent_card():
                    client = A2AClient()
                    try:
                        card = await client.get_agent_card('http://host.docker.internal:$serverPort')
                        print(json.dumps({
                            'name': card.name,
                            'capabilities': card.capabilities,
                            'skills': [skill.name for skill in card.skills]
                        }))
                        return True
                    except Exception as e:
                        print(f"Error: {e}")
                        return False
                
                if __name__ == "__main__":
                    result = asyncio.run(test_agent_card())
                    exit(0 if result else 1)
            """.trimIndent()
            
            // When
            val result = pythonClientContainer.execInContainer(
                "python", "-c", testScript
            )
            
            // Then
            result.exitCode shouldBe 0
            val output = result.stdout
            output should contain("Echo Tool")
            output should contain("Reverse Tool")
        }
        
        test("should execute Koog tools via A2A protocol") {
            // Given
            val testScript = """
                import asyncio
                import json
                from a2a_python import A2AClient, Message, MessagePart
                
                async def test_echo_tool():
                    client = A2AClient()
                    try:
                        message = Message(parts=[
                            MessagePart.structured_data({
                                'text': 'Hello from Python A2A client!'
                            })
                        ])
                        response = await client.send_message(
                            'http://host.docker.internal:$serverPort',
                            message,
                            skill='echo'
                        )
                        print(json.dumps({
                            'status': response.task.status,
                            'result': response.artifacts[0].content if response.artifacts else None
                        }))
                        return True
                    except Exception as e:
                        print(f"Error: {e}")
                        return False
                
                if __name__ == "__main__":
                    result = asyncio.run(test_echo_tool())
                    exit(0 if result else 1)
            """.trimIndent()
            
            // When
            val result = pythonClientContainer.execInContainer(
                "python", "-c", testScript
            )
            
            // Then
            result.exitCode shouldBe 0
            val output = result.stdout
            output should contain("COMPLETED")
            output should contain("Hello from Python A2A client!")
        }
        
        test("should handle concurrent requests") {
            // Test multiple simultaneous requests
            val concurrentRequests = (1..5).map { index ->
                async {
                    val testScript = """
                        import asyncio
                        from a2a_python import A2AClient, Message, MessagePart
                        
                        async def test_concurrent():
                            client = A2AClient()
                            message = Message(parts=[
                                MessagePart.structured_data({
                                    'text': f'Concurrent request $index'
                                })
                            ])
                            response = await client.send_message(
                                'http://host.docker.internal:$serverPort',
                                message,
                                skill='echo'
                            )
                            return response.task.status == 'COMPLETED'
                        
                        result = asyncio.run(test_concurrent())
                        exit(0 if result else 1)
                    """.trimIndent()
                    
                    pythonClientContainer.execInContainer("python", "-c", testScript)
                }
            }
            
            // When
            val results = runBlocking {
                concurrentRequests.awaitAll()
            }
            
            // Then
            results.all { it.exitCode == 0 } shouldBe true
        }
    }
    
    context("Bi-directional Communication") {
        test("should enable Koog agent to call external A2A agent") {
            // Set up external test agent
            val testAgentContainer = A2ATestConfig.createTestAgentContainer(network)
            testAgentContainer.start()
            
            try {
                // Configure Koog agent with A2A client capability
                val a2aClient = KtorA2AClient(HttpClient(CIO), A2AClientConfig())
                val remoteAgentTool = RemoteAgentTool(
                    agentUrl = "http://test-agent:8080",
                    skill = "reverse",
                    client = a2aClient
                )
                
                // Test that Koog server can use remote agent tool
                val testScript = """
                    import asyncio
                    from a2a_python import A2AClient, Message, MessagePart
                    
                    async def test_remote_call():
                        client = A2AClient()
                        message = Message(parts=[
                            MessagePart.structured_data({
                                'text': 'test reverse',
                                'use_remote_agent': True
                            })
                        ])
                        response = await client.send_message(
                            'http://host.docker.internal:$serverPort',
                            message,
                            skill='remote_reverse'
                        )
                        return 'esrever tset' in str(response.artifacts[0].content)
                    
                    result = asyncio.run(test_remote_call())
                    exit(0 if result else 1)
                """.trimIndent()
                
                val result = pythonClientContainer.execInContainer("python", "-c", testScript)
                result.exitCode shouldBe 0
                
            } finally {
                testAgentContainer.stop()
            }
        }
    }
})
```

### Multi-Agent Workflow Tests
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2AMultiAgentWorkflowTest.kt

class A2AMultiAgentWorkflowTest : FunSpec({
    
    lateinit var network: Network
    lateinit var testAgentContainer: GenericContainer<Nothing>
    lateinit var coordinatorAgentContainer: GenericContainer<Nothing>
    lateinit var koogServer: A2AServer
    
    beforeSpec {
        network = A2ATestConfig.createTestNetwork()
        
        // Start test agents
        testAgentContainer = A2ATestConfig.createTestAgentContainer(network)
        testAgentContainer.start()
        
        coordinatorAgentContainer = GenericContainer<Nothing>(A2ATestConfig.COORDINATOR_AGENT_IMAGE)
            .withNetwork(network)
            .withNetworkAliases("coordinator-agent")
            .withExposedPorts(8081)
            .withStartupTimeout(Duration.ofMinutes(2))
            .waitingFor(Wait.forHttp("/health").forStatusCode(200))
            
        coordinatorAgentContainer.start()
        
        // Start Koog server with A2A client capabilities
        val agent = createMultiAgentCapableKoogAgent()
        koogServer = A2AServer(
            config = A2AServerConfig(port = 0),
            agentBridge = A2AAgentBridge(agent)
        )
        koogServer.start()
    }
    
    afterSpec {
        koogServer.stop()
        testAgentContainer.stop()
        coordinatorAgentContainer.stop()
        network.close()
    }
    
    context("Multi-Agent Workflows") {
        test("should coordinate tasks across multiple A2A agents") {
            // Test complex workflow involving multiple agents
            val workflowDefinition = A2AWorkflow(
                steps = listOf(
                    A2AWorkflowStep(
                        agentUrl = "http://test-agent:8080",
                        skill = "echo",
                        input = mapOf("text" to "Initial text")
                    ),
                    A2AWorkflowStep(
                        agentUrl = "http://coordinator-agent:8081", 
                        skill = "coordinate_text_processing",
                        input = mapOf(
                            "text" to "\${previous.response}",
                            "operations" to listOf("reverse", "uppercase")
                        )
                    ),
                    A2AWorkflowStep(
                        agentUrl = "http://host.docker.internal:${koogServer.getPort()}",
                        skill = "finalize_processing",
                        input = mapOf("results" to "\${previous.coordinated_results}")
                    )
                )
            )
            
            // Execute workflow and verify results
            val workflowEngine = A2AWorkflowEngine()
            val result = workflowEngine.executeWorkflow(workflowDefinition)
            
            result.status shouldBe A2AWorkflowStatus.COMPLETED
            result.steps should haveSize(3)
            result.steps.all { it.status == A2AStepStatus.COMPLETED } shouldBe true
        }
        
        test("should handle agent failures in workflow") {
            // Test error handling and recovery in multi-agent scenarios
        }
        
        test("should support parallel agent execution") {
            // Test concurrent execution of multiple agents
        }
    }
})
```

## Performance Testing

### Load Testing with TestContainers
```kotlin
// agents-a2a-integration-tests/src/jvmTest/kotlin/ai/koog/agents/a2a/integration/A2APerformanceTest.kt

class A2APerformanceTest : FunSpec({
    
    test("should handle high concurrency") {
        val testAgentContainer = A2ATestConfig.createTestAgentContainer(
            A2ATestConfig.createTestNetwork()
        )
        testAgentContainer.start()
        
        try {
            val client = KtorA2AClient(HttpClient(CIO), A2AClientConfig())
            val agentUrl = "http://${testAgentContainer.host}:${testAgentContainer.getMappedPort(8080)}"
            
            // Test with 100 concurrent requests
            val startTime = System.currentTimeMillis()
            val requests = (1..100).map { index ->
                async {
                    client.sendMessage(
                        agentUrl,
                        A2AMessage(
                            parts = listOf(
                                A2AMessagePart.StructuredData(
                                    data = mapOf(
                                        "skill" to "echo",
                                        "text" to "Load test $index"
                                    )
                                )
                            )
                        )
                    )
                }
            }
            
            val responses = runBlocking { requests.awaitAll() }
            val endTime = System.currentTimeMillis()
            
            // Assertions
            responses should haveSize(100)
            responses.all { it.task.status == A2ATaskStatus.COMPLETED } shouldBe true
            (endTime - startTime) should beLessThan(30000) // Complete within 30 seconds
            
        } finally {
            testAgentContainer.stop()
        }
    }
})
```

## Docker Compose for Complex Scenarios

### Multi-Agent Test Environment
```yaml
# docker/docker-compose.test.yml
version: '3.8'

services:
  test-agent:
    build: ./test-a2a-agent
    ports:
      - "8080:8080"
    environment:
      - AGENT_NAME=Test Agent
      - LOG_LEVEL=INFO
    healthcheck:
      test: ["CMD", "python", "health_check.py"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - a2a-test

  coordinator-agent:
    build: ./multi-agent-test
    ports:
      - "8081:8080"
    environment:
      - AGENT_NAME=Coordinator Agent
      - LOG_LEVEL=INFO
    depends_on:
      test-agent:
        condition: service_healthy
    networks:
      - a2a-test

  specialized-agent-1:
    build: ./specialized-agents/math-agent
    ports:
      - "8082:8080"
    networks:
      - a2a-test

  specialized-agent-2:
    build: ./specialized-agents/text-agent  
    ports:
      - "8083:8080"
    networks:
      - a2a-test

  koog-server:
    image: koog-a2a-server:test
    ports:
      - "8090:8090"
    environment:
      - SERVER_PORT=8090
      - A2A_CLIENT_AGENTS=test-agent:8080,coordinator-agent:8080
    depends_on:
      - test-agent
      - coordinator-agent
    networks:
      - a2a-test

networks:
  a2a-test:
    driver: bridge

volumes:
  test-data:
```

## Continuous Integration Integration

### GitHub Actions Workflow
```yaml
# .github/workflows/a2a-integration-tests.yml
name: A2A Integration Tests

on:
  push:
    branches: [ main, 'feature/a2a-*' ]
  pull_request:
    branches: [ main ]

jobs:
  a2a-integration-tests:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Setup Gradle
      uses: gradle/gradle-build-action@v2
      
    - name: Build test Docker images
      run: |
        docker build -t koog-test-a2a-agent:latest docker/test-a2a-agent/
        docker build -t koog-coordinator-agent:latest docker/multi-agent-test/
        
    - name: Run A2A integration tests
      run: ./gradlew :agents:agents-a2a:agents-a2a-integration-tests:test
      
    - name: Upload test results
      uses: actions/upload-artifact@v4
      if: always()
      with:
        name: a2a-test-results
        path: |
          **/build/reports/tests/
          **/build/test-results/
```

This comprehensive testing strategy ensures robust validation of A2A protocol integration with real-world scenarios using TestContainers and the official Google A2A Python SDK.