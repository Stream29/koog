package ai.koog.agents.mcp

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAgentToolsApi::class)
object TestToolEnabler : DirectToolCallsEnabler

class McpToolTest {
    private val testPort = 3001
    private val testServer = TestMcpServer(testPort)

    @BeforeEach
    fun setup() {
        testServer.start()
    }

    @AfterEach
    fun tearDown() {
        testServer.stop()
    }

    @OptIn(InternalAgentToolsApi::class)
    @Test
    fun `test McpTool with SSE transport`() = runTest(timeout = 30.seconds) {
        // Create a tool registry using McpToolRegistryProvider.fromTransport
        val toolRegistry = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                McpToolRegistryProvider.fromTransport(
                    transport = McpToolRegistryProvider.defaultSseTransport("http://localhost:$testPort/sse"),
                    name = "test-client",
                    version = "0.1.0"
                )
            }
        }

        // A list of tools that the server is expected to provide
        val expectedToolDescriptors = listOf(
            ToolDescriptor(
                name = "greeting",
                description = "A simple greeting tool",
                requiredParameters = listOf(
                    ToolParameterDescriptor(
                        name = "name",
                        type = ToolParameterType.String,
                        description = "A name to greet",
                    )
                ),
                optionalParameters = listOf(
                    ToolParameterDescriptor(
                        name = "title",
                        type = ToolParameterType.String,
                        description = "Title to use in the greeting",
                    )
                )
            )
        )

        // Actual list of tools provided
        val actualToolDescriptor = toolRegistry.tools.map { it.descriptor }
        assertEquals(expectedToolDescriptors, actualToolDescriptor)

        // Now test the actual tool
        val greetingTool = toolRegistry.getTool("greeting") as McpTool
        val args = McpTool.Args(buildJsonObject { put("name", "Test") })

        val result = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                greetingTool.execute(args, TestToolEnabler)
            }
        }

        assertEquals("Hello, Test!", result)

        val argsWithTitle = McpTool.Args(
            buildJsonObject {
                put("name", "Test")
                put("title", "Mr.")
            }
        )
        val resultWithTitle = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(1.minutes) {
                greetingTool.execute(argsWithTitle, TestToolEnabler)
            }
        }

        assertEquals("Hello, Mr. Test!", resultWithTitle)
    }
}
