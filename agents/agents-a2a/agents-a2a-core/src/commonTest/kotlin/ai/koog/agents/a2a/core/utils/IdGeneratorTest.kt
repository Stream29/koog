package ai.koog.agents.a2a.core.utils

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdGeneratorTest {
    @Test
    fun `should generate unique task IDs`() {
        // When
        val id1 = IdGenerator.generateTaskId()
        val id2 = IdGenerator.generateTaskId()
        
        // Then
        assertTrue(id1.startsWith("task_"))
        assertTrue(id2.startsWith("task_"))
        assertNotEquals(id1, id2)
    }
    
    @Test
    fun `should generate unique message IDs`() {
        // When
        val id1 = IdGenerator.generateMessageId()
        val id2 = IdGenerator.generateMessageId()
        
        // Then
        assertTrue(id1.startsWith("msg_"))
        assertTrue(id2.startsWith("msg_"))
        assertNotEquals(id1, id2)
    }
    
    @Test
    fun `should generate unique artifact IDs`() {
        // When
        val id1 = IdGenerator.generateArtifactId()
        val id2 = IdGenerator.generateArtifactId()
        
        // Then
        assertTrue(id1.startsWith("artifact_"))
        assertTrue(id2.startsWith("artifact_"))
        assertNotEquals(id1, id2)
    }
    
    @Test
    fun `should generate unique request IDs`() {
        // When
        val id1 = IdGenerator.generateRequestId()
        val id2 = IdGenerator.generateRequestId()
        
        // Then
        assertTrue(id1.startsWith("req_"))
        assertTrue(id2.startsWith("req_"))
        assertNotEquals(id1, id2)
    }
    
    @Test
    fun `should generate unique webhook IDs`() {
        // When
        val id1 = IdGenerator.generateWebhookId()
        val id2 = IdGenerator.generateWebhookId()
        
        // Then
        assertTrue(id1.startsWith("webhook_"))
        assertTrue(id2.startsWith("webhook_"))
        assertNotEquals(id1, id2)
    }
}