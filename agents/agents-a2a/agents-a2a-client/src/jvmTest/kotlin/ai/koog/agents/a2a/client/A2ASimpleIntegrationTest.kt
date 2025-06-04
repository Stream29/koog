package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.models.*
import ai.koog.agents.a2a.core.utils.A2AMethodConstants
import ai.koog.agents.a2a.core.utils.IdGenerator
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Simple integration test that verifies the A2A client works with a mock A2A server.
 * This serves as a basic end-to-end test without requiring Docker containers.
 */
class A2ASimpleIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `should perform complete A2A workflow with mock server`() = runBlocking {
        // Given - Create a mock A2A server that follows the protocol
        val agentCard = AgentCard(
            name = "Mock A2A Agent",
            description = "A mock agent for testing A2A protocol compliance",
            version = "1.0.0",
            capabilities = listOf("text_processing", "echo"),
            skills = listOf(
                Skill(
                    name = "echo",
                    description = "Echoes back the input text",
                    parameters = JsonSchema(
                        type = "object",
                        properties = mapOf(
                            "text" to JsonSchemaProperty(
                                type = "string",
                                description = "Text to echo back"
                            )
                        ),
                        required = listOf("text")
                    )
                )
            )
        )

        val mockTaskId = IdGenerator.generateTaskId()
        val mockTask = Task(
            id = mockTaskId,
            status = TaskStatus.COMPLETED,
            messages = emptyList(),
            artifacts = emptyList(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            completedAt = Clock.System.now()
        )

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            
            when {
                // Agent card endpoint
                url.endsWith("/.well-known/agent.json") -> {
                    respond(
                        content = json.encodeToString(AgentCard.serializer(), agentCard),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                
                // A2A JSON-RPC endpoint
                url.endsWith("/a2a") -> {
                    val body = request.body.toByteArray().decodeToString()
                    val jsonRpcRequest = json.decodeFromString<JsonRpcRequest>(body)
                    
                    when (jsonRpcRequest.method) {
                        A2AMethodConstants.MESSAGE_SEND -> {
                            val sendResponse = MessageSendResponse(task = mockTask)
                            val jsonResponse = JsonRpcResponse(
                                result = json.encodeToJsonElement(MessageSendResponse.serializer(), sendResponse),
                                id = jsonRpcRequest.id
                            )
                            respond(
                                content = json.encodeToString(JsonRpcResponse.serializer(), jsonResponse),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                        
                        A2AMethodConstants.TASKS_GET -> {
                            val getResponse = TaskGetResponse(task = mockTask)
                            val jsonResponse = JsonRpcResponse(
                                result = json.encodeToJsonElement(TaskGetResponse.serializer(), getResponse),
                                id = jsonRpcRequest.id
                            )
                            respond(
                                content = json.encodeToString(JsonRpcResponse.serializer(), jsonResponse),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                        
                        A2AMethodConstants.TASKS_CANCEL -> {
                            val cancelledTask = mockTask.copy(status = TaskStatus.CANCELLED)
                            val cancelResponse = TaskCancelResponse(task = cancelledTask)
                            val jsonResponse = JsonRpcResponse(
                                result = json.encodeToJsonElement(TaskCancelResponse.serializer(), cancelResponse),
                                id = jsonRpcRequest.id
                            )
                            respond(
                                content = json.encodeToString(JsonRpcResponse.serializer(), jsonResponse),
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                        
                        else -> {
                            val errorResponse = JsonRpcResponse(
                                error = JsonRpcError(
                                    code = -32601,
                                    message = "Method not found: ${jsonRpcRequest.method}"
                                ),
                                id = jsonRpcRequest.id
                            )
                            respond(
                                content = json.encodeToString(JsonRpcResponse.serializer(), errorResponse),
                                status = HttpStatusCode.BadRequest,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                    }
                }
                
                else -> {
                    respond(
                        content = "Not Found",
                        status = HttpStatusCode.NotFound
                    )
                }
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val client = KtorA2AClient(httpClient)
        val agentUrl = "http://mock-a2a-agent"

        // When & Then - Test complete A2A workflow

        // 1. Get agent card
        val retrievedAgentCard = client.getAgentCard(agentUrl)
        assertEquals("Mock A2A Agent", retrievedAgentCard.name)
        assertEquals("1.0.0", retrievedAgentCard.version)
        assertTrue(retrievedAgentCard.capabilities.contains("text_processing"))
        assertEquals(1, retrievedAgentCard.skills.size)
        assertEquals("echo", retrievedAgentCard.skills.first().name)

        // 2. Send a message
        val message = Message(
            id = IdGenerator.generateMessageId(),
            taskId = IdGenerator.generateTaskId(),
            direction = MessageDirection.INBOUND,
            parts = listOf(
                MessagePart.Text("Hello, mock A2A agent!"),
                MessagePart.StructuredData(
                    data = mapOf("command" to "echo", "text" to "integration test")
                )
            ),
            timestamp = Clock.System.now()
        )

        val completedTask = client.sendMessage(agentUrl, message)
        assertEquals(mockTaskId, completedTask.id)
        assertEquals(TaskStatus.COMPLETED, completedTask.status)
        assertNotNull(completedTask.createdAt)
        assertNotNull(completedTask.updatedAt)
        assertNotNull(completedTask.completedAt)

        // 3. Get task details
        val taskDetails = client.getTask(
            agentUrl = agentUrl,
            taskId = completedTask.id,
            includeMessages = true,
            includeArtifacts = true
        )
        assertEquals(completedTask.id, taskDetails.id)
        assertEquals(TaskStatus.COMPLETED, taskDetails.status)

        // 4. Test streaming (fallback to polling)
        val streamResponses = mutableListOf<MessageStreamResponse>()
        client.sendMessageStream(agentUrl, message).collect { response ->
            streamResponses.add(response)
        }
        assertTrue(streamResponses.isNotEmpty())
        val finalStreamResponse = streamResponses.last()
        assertEquals(TaskStatus.COMPLETED, finalStreamResponse.task.status)

        // 5. Test task cancellation
        val cancelledTask = client.cancelTask(agentUrl, completedTask.id, "Integration test cancellation")
        assertEquals(completedTask.id, cancelledTask.id)
        assertEquals(TaskStatus.CANCELLED, cancelledTask.status)

        // Cleanup
        client.close()
    }

    @Test
    fun `should handle authentication headers correctly`() = runBlocking {
        // Given
        var capturedHeaders: Headers? = null
        
        val mockEngine = MockEngine { request ->
            capturedHeaders = request.headers
            
            // Return a simple agent card response
            val agentCard = AgentCard(
                name = "Auth Test Agent",
                description = "Testing authentication",
                version = "1.0.0",
                capabilities = emptyList(),
                skills = emptyList()
            )
            
            respond(
                content = json.encodeToString(AgentCard.serializer(), agentCard),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        val client = KtorA2AClient(httpClient)

        // When - Test different authentication schemes
        val apiKeyAuth = AuthenticationContext(
            scheme = AuthenticationScheme.API_KEY,
            credentials = mapOf("apiKey" to "test-api-key-123")
        )

        client.getAgentCard("http://test-agent", apiKeyAuth)

        // Then
        assertNotNull(capturedHeaders)
        assertEquals("test-api-key-123", capturedHeaders["X-API-Key"])

        // Test Bearer token authentication
        val bearerAuth = AuthenticationContext(
            scheme = AuthenticationScheme.BEARER_TOKEN,
            credentials = mapOf("token" to "test-bearer-token")
        )

        client.getAgentCard("http://test-agent", bearerAuth)

        // Then
        assertEquals("Bearer test-bearer-token", capturedHeaders["Authorization"])

        client.close()
    }

    @Test
    fun `should create client with SSE support configured`() = runBlocking {
        // Given
        val mockEngine = MockEngine { request ->
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }

        // When - Create client (this should install SSE plugin)
        val client = KtorA2AClient(httpClient)

        // Then - Verify client was created successfully
        assertNotNull(client)
        
        // Verify that sendMessageStream method is available and properly typed
        val message = Message(
            id = IdGenerator.generateMessageId(),
            taskId = IdGenerator.generateTaskId(),
            direction = MessageDirection.INBOUND,
            parts = listOf(MessagePart.Text("Test SSE streaming")),
            timestamp = Clock.System.now()
        )

        // Just verify the method returns a Flow and can be instantiated
        val streamFlow = client.sendMessageStream("http://test-agent", message)
        assertNotNull(streamFlow)

        client.close()
    }
}