package ai.koog.agents.a2a.core.models

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TaskTest {
    @Test
    fun `should serialize and deserialize Task correctly`() {
        // Given
        val now = Clock.System.now()
        val task = Task(
            id = "task_123",
            status = TaskStatus.RUNNING,
            messages = emptyList(),
            artifacts = emptyList(),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            metadata = mapOf("priority" to "high")
        )
        
        // When
        val json = Json.encodeToString(task)
        val deserialized = Json.decodeFromString<Task>(json)
        
        // Then
        assertEquals(task.id, deserialized.id)
        assertEquals(task.status, deserialized.status)
        assertEquals(task.createdAt, deserialized.createdAt)
        assertEquals(task.metadata, deserialized.metadata)
    }
    
    @Test
    fun `should handle all TaskStatus values`() {
        // Test all enum values can be serialized/deserialized
        val statuses = TaskStatus.entries
        
        for (status in statuses) {
            val json = Json.encodeToString(status)
            val deserialized = Json.decodeFromString<TaskStatus>(json)
            assertEquals(status, deserialized)
        }
    }
    
    @Test
    fun `TaskConfig should have sensible defaults`() {
        // Given
        val config = TaskConfig()
        
        // Then
        assertEquals(TaskPriority.NORMAL, config.priority)
        assertEquals(0, config.maxRetries)
        assertNotNull(config.metadata)
    }
}