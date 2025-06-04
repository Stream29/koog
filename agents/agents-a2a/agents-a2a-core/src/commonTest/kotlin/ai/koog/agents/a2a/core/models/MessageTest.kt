package ai.koog.agents.a2a.core.models

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageTest {
    @Test
    fun `should serialize and deserialize Message with Text parts`() {
        // Given
        val message = Message(
            id = "msg_123",
            taskId = "task_456",
            direction = MessageDirection.INBOUND,
            parts = listOf(
                MessagePart.Text("Hello, world!")
            ),
            timestamp = Clock.System.now(),
            metadata = mapOf("source" to "user")
        )
        
        // When
        val json = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<Message>(json)
        
        // Then
        assertEquals(message.id, deserialized.id)
        assertEquals(message.taskId, deserialized.taskId)
        assertEquals(message.direction, deserialized.direction)
        assertEquals(message.parts.size, deserialized.parts.size)
        assertEquals(message.metadata, deserialized.metadata)
        
        val textPart = deserialized.parts.first() as MessagePart.Text
        assertEquals("Hello, world!", textPart.content)
    }
    
    @Test
    fun `should serialize and deserialize Message with File parts`() {
        // Given
        val fileContent = "Hello, file!".encodeToByteArray()
        val message = Message(
            id = "msg_123",
            taskId = "task_456",
            direction = MessageDirection.OUTBOUND,
            parts = listOf(
                MessagePart.File(
                    name = "test.txt",
                    mimeType = "text/plain",
                    content = fileContent,
                    size = fileContent.size.toLong()
                )
            ),
            timestamp = Clock.System.now()
        )
        
        // When
        val json = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<Message>(json)
        
        // Then
        val filePart = deserialized.parts.first() as MessagePart.File
        assertEquals("test.txt", filePart.name)
        assertEquals("text/plain", filePart.mimeType)
        assertTrue(filePart.content.contentEquals(fileContent))
        assertEquals(fileContent.size.toLong(), filePart.size)
    }
    
    @Test
    fun `should serialize and deserialize Message with StructuredData parts`() {
        // Given
        val message = Message(
            id = "msg_123",
            taskId = "task_456",
            direction = MessageDirection.INBOUND,
            parts = listOf(
                MessagePart.StructuredData(
                    schema = "user-input",
                    data = mapOf("query" to "What is the weather?", "location" to "Paris")
                )
            ),
            timestamp = Clock.System.now()
        )
        
        // When
        val json = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<Message>(json)
        
        // Then
        val structuredPart = deserialized.parts.first() as MessagePart.StructuredData
        assertEquals("user-input", structuredPart.schema)
        assertEquals("What is the weather?", structuredPart.data["query"])
        assertEquals("Paris", structuredPart.data["location"])
    }
    
    @Test
    fun `should serialize and deserialize Message with Reference parts`() {
        // Given
        val message = Message(
            id = "msg_123",
            taskId = "task_456",
            direction = MessageDirection.INBOUND,
            parts = listOf(
                MessagePart.Reference(
                    url = "https://example.com/document.pdf",
                    resourceType = "application/pdf",
                    description = "Important document"
                )
            ),
            timestamp = Clock.System.now()
        )
        
        // When
        val json = Json.encodeToString(message)
        val deserialized = Json.decodeFromString<Message>(json)
        
        // Then
        val referencePart = deserialized.parts.first() as MessagePart.Reference
        assertEquals("https://example.com/document.pdf", referencePart.url)
        assertEquals("application/pdf", referencePart.resourceType)
        assertEquals("Important document", referencePart.description)
    }
    
    @Test
    fun `MessagePart File should handle equality correctly`() {
        // Given
        val content1 = "Hello".encodeToByteArray()
        val content2 = "Hello".encodeToByteArray()
        val content3 = "World".encodeToByteArray()
        
        val file1 = MessagePart.File("test.txt", "text/plain", content1, content1.size.toLong())
        val file2 = MessagePart.File("test.txt", "text/plain", content2, content2.size.toLong())
        val file3 = MessagePart.File("test.txt", "text/plain", content3, content3.size.toLong())
        
        // Then
        assertEquals(file1, file2) // Same content
        assertTrue(file1 != file3) // Different content
    }
}