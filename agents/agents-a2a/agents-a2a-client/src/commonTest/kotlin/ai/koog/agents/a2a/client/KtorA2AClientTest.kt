package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.models.*
import ai.koog.agents.a2a.core.utils.A2AMethodConstants
import ai.koog.agents.a2a.core.utils.IdGenerator
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KtorA2AClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `should create client with default config`() {
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
        
        // When
        val client = KtorA2AClient(httpClient)
        
        // Then
        assertNotNull(client)
    }

    @Test
    fun `should handle sendMessage with mocked response`() = runTest {
        // Given
        val taskId = IdGenerator.generateTaskId()
        val now = Clock.System.now()
        
        val expectedTask = Task(
            id = taskId,
            status = TaskStatus.COMPLETED,
            messages = emptyList(),
            artifacts = emptyList(),
            createdAt = now,
            updatedAt = now,
            completedAt = now
        )
        
        val sendResponse = MessageSendResponse(task = expectedTask)
        val jsonResponse = JsonRpcResponse(
            result = json.encodeToJsonElement(MessageSendResponse.serializer(), sendResponse),
            id = "req_123"
        )
        
        val getResponse = TaskGetResponse(task = expectedTask)
        val getJsonResponse = JsonRpcResponse(
            result = json.encodeToJsonElement(TaskGetResponse.serializer(), getResponse),
            id = "req_124"
        )
        
        val mockEngine = MockEngine { request ->
            val requestUrl = request.url.toString()
            
            when {
                requestUrl.endsWith("/a2a") -> {
                    val body = request.body.toByteArray().decodeToString()
                    val jsonRpcRequest = json.decodeFromString<JsonRpcRequest>(body)
                    
                    when (jsonRpcRequest.method) {
                        A2AMethodConstants.MESSAGE_SEND -> respond(
                            content = json.encodeToString(JsonRpcResponse.serializer(), jsonResponse),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        A2AMethodConstants.TASKS_GET -> respond(
                            content = json.encodeToString(JsonRpcResponse.serializer(), getJsonResponse),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                        else -> respond(
                            content = "{}",
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound
                )
            }
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val client = KtorA2AClient(httpClient)
        
        val message = Message(
            id = IdGenerator.generateMessageId(),
            taskId = taskId,
            direction = MessageDirection.INBOUND,
            parts = listOf(MessagePart.Text("Hello, A2A!")),
            timestamp = now
        )
        
        // When
        val result = client.sendMessage("http://test-agent", message)
        
        // Then
        assertEquals(taskId, result.id)
        assertEquals(TaskStatus.COMPLETED, result.status)
    }

    @Test
    fun `should handle getAgentCard`() = runTest {
        // Given
        val agentCard = AgentCard(
            name = "Test Agent",
            description = "A test agent",
            version = "1.0.0",
            capabilities = listOf("text_processing"),
            skills = listOf(
                Skill(
                    name = "echo",
                    description = "Echo text",
                    parameters = JsonSchema(type = "object")
                )
            )
        )
        
        val mockEngine = MockEngine { request ->
            when {
                request.url.encodedPath == "/.well-known/agent.json" -> {
                    respond(
                        content = json.encodeToString(AgentCard.serializer(), agentCard),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound
                )
            }
        }
        
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val client = KtorA2AClient(httpClient)
        
        // When
        val result = client.getAgentCard("http://test-agent")
        
        // Then
        assertEquals("Test Agent", result.name)
        assertEquals("1.0.0", result.version)
        assertEquals(1, result.skills.size)
        assertEquals("echo", result.skills.first().name)
    }

    @Test
    fun `should handle error responses`() = runTest {
        // Given
        val errorResponse = JsonRpcResponse(
            error = JsonRpcError(
                code = -40001,
                message = "Agent unavailable"
            ),
            id = "req_123"
        )
        
        val mockEngine = MockEngine { request ->
            respond(
                content = json.encodeToString(JsonRpcResponse.serializer(), errorResponse),
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
        
        val message = Message(
            id = IdGenerator.generateMessageId(),
            taskId = IdGenerator.generateTaskId(),
            direction = MessageDirection.INBOUND,
            parts = listOf(MessagePart.Text("Hello")),
            timestamp = Clock.System.now()
        )
        
        // When & Then
        try {
            client.sendMessage("http://test-agent", message)
            kotlin.test.fail("Expected A2AClientException")
        } catch (e: A2AClientException) {
            assertTrue(e.message?.contains("Agent unavailable") == true)
            assertEquals(-40001, e.jsonRpcError?.code)
        }
    }
}