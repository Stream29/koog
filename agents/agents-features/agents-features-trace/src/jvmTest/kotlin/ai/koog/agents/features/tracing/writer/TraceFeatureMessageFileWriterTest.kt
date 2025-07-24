package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.tracing.*
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.mock.MockLLMProvider
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TraceFeatureMessageFileWriterTest {

    companion object {
        private fun createTempLogFile(tempDir: Path) = Files.createTempFile(tempDir, "agent-trace", ".log")

        private fun sinkOpener(path: Path): Sink {
            return SystemFileSystem.sink(path = kotlinx.io.files.Path(path.pathString)).buffered()
        }
    }

    @Test
    fun `test file stream feature provider collect events on agent run`(@TempDir tempDir: Path) = runTest {
        TraceFeatureMessageFileWriter(
            targetPath = createTempLogFile(tempDir),
            sinkOpener = TraceFeatureMessageFileWriterTest::sinkOpener
        ).use { writer ->

            // Agent Config
            val agentId = "test-agent-id"
            val strategyName = "test-strategy"

            val userPrompt = "Call the dummy tool with argument: test"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val mockResponse = "Return test result"

            // Tools
            val dummyTool = DummyTool()

            val toolRegistry = ToolRegistry {
                tool(dummyTool)
            }

            // Model
            val testModel = LLModel(
                provider = MockLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList(),
                contextLength = 1_000,
            )

            // Prompt
            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt)
                ),
                id = promptId
            )

            val expectedLLMCallPrompt = expectedPrompt.copy(
                messages = expectedPrompt.messages + userMessage(content = userPrompt)
            )

            val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
                messages = expectedPrompt.messages + listOf(
                    userMessage(content = userPrompt),
                    toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                    toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toMessage(clock = testClock)
                )
            )

            val strategy = strategy(strategyName) {
                val nodeSendInput by nodeLLMRequest("test-llm-call")
                val nodeExecuteTool by nodeExecuteTool("test-tool-call")
                val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

                edge(nodeStart forwardTo nodeSendInput)
                edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            }

            val mockExecutor = getMockExecutor(clock = testClock) {
                mockLLMToolCall(tool = dummyTool, args = DummyTool.Args("test"), toolCallId = "0") onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            var runId: String = ""

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) { runId = message.runId }
                        true
                    }
                    addMessageProcessor(writer)
                }
            }.use { agent ->
                agent.run(userPrompt)
            }

            val expectedResponse = assistantMessage(content = "Default test response")

            val expectedMessages = listOf(
                "${AIAgentStartedEvent::class.simpleName} (agent id: ${agentId}, run id: ${runId}, strategy: $strategyName)",
                "${AIAgentStrategyStartEvent::class.simpleName} (run id: ${runId}, strategy: $strategyName)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: __start__, input: $userPrompt)",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: __start__, input: $userPrompt, output: $userPrompt)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: test LLM call, input: Test LLM call prompt)",
                "${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: test LLM call, input: Test LLM call prompt, output: $expectedResponse)",
                "${AIAgentNodeExecutionStartEvent::class.simpleName} (run id: ${runId}, node: test LLM call with tools, input: Test LLM call with tools prompt)",
                "${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            toolCallMessage(toolName = "Tool", content = "")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "${AIAgentNodeExecutionEndEvent::class.simpleName} (run id: ${runId}, node: test LLM call with tools, input: Test LLM call with tools prompt, output: $expectedResponse)",
                "${AIAgentStrategyFinishedEvent::class.simpleName} (run id: ${runId}, strategy: $strategyName, result: Done)",
                "${AIAgentFinishedEvent::class.simpleName} (agent id: ${agentId}, run id: ${runId}, result: Done)",
                "${AIAgentBeforeCloseEvent::class.simpleName} (agent id: ${agentId})",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessages.size)
            assertContentEquals(expectedMessages, actualMessages)
        }
    }

    @Test
    fun `test feature message log writer with custom format function for direct message processing`(@TempDir tempDir: Path) = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            when (message) {
                is FeatureStringMessage -> "CUSTOM STRING. ${message.message}"
                is FeatureEvent -> "CUSTOM EVENT. ${message.eventId}"
                else -> "CUSTOM OTHER: ${message::class.simpleName}"
            }
        }

        val agentId = "test-agent-id"
        val runId = "test-run-id"
        val strategyName = "test-strategy"

        val messagesToProcess = listOf(
            FeatureStringMessage("Test string message"),
            AIAgentStartedEvent(agentId = agentId, runId = runId, strategyName = strategyName)
        )

        val expectedMessages = listOf(
            "CUSTOM STRING. Test string message",
            "CUSTOM EVENT. ${AIAgentStartedEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener,
            format = customFormat
        ).use { writer ->
            writer.initialize()

            messagesToProcess.forEach { message -> writer.processMessage(message) }

            val actualMessage = writer.targetPath.readLines()

            assertEquals(expectedMessages.size, actualMessage.size)
            assertContentEquals(expectedMessages, actualMessage)
        }
    }

    @Test
    fun `test feature message log writer with custom format function`(@TempDir tempDir: Path) = runTest {
        val customFormat: (FeatureMessage) -> String = { message ->
            "CUSTOM. ${message::class.simpleName}"
        }

        val expectedEvents = listOf(
            "CUSTOM. ${AIAgentStartedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionStartEvent::class.simpleName}",
            "CUSTOM. ${BeforeLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AfterLLMCallEvent::class.simpleName}",
            "CUSTOM. ${AIAgentNodeExecutionEndEvent::class.simpleName}",
            "CUSTOM. ${AIAgentStrategyFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentFinishedEvent::class.simpleName}",
            "CUSTOM. ${AIAgentBeforeCloseEvent::class.simpleName}",
        )

        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener,
            format = customFormat
        ).use { writer ->
            val strategyName = "tracing-test-strategy"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    messageFilter = { true }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")
            agent.close()

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedEvents.size, actualMessages.size)
            assertContentEquals(expectedEvents, actualMessages)
        }
    }

    @Test
    fun `test file stream feature provider is not set`(@TempDir tempDir: Path) = runTest {

        val logFile = createTempLogFile(tempDir)
        TraceFeatureMessageFileWriter(logFile, TraceFeatureMessageFileWriterTest::sinkOpener).use { writer ->

            val strategyName = "tracing-test-strategy"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val agent = createAgent(strategy = strategy) {
                install(Tracing) {
                    messageFilter = { true }
                }
            }

            agent.run("")
            agent.close()

            assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
            assertEquals(emptyList(), logFile.readLines())
        }

        assertEquals(listOf(logFile), tempDir.listDirectoryEntries())
        assertEquals(emptyList(), logFile.readLines())
    }

    @Test
    fun `test logger stream feature provider message filter`(@TempDir tempDir: Path) = runTest {
        TraceFeatureMessageFileWriter(
            createTempLogFile(tempDir),
            TraceFeatureMessageFileWriterTest::sinkOpener
        ).use { writer ->

            val strategyName = "tracing-test-strategy"

            val userPrompt = "Test user prompt"
            val systemPrompt = "Test system prompt"
            val assistantPrompt = "Test assistant prompt"
            val promptId = "Test prompt id"

            val strategy = strategy<String, String>(strategyName) {
                val llmCallNode by nodeLLMRequest("test LLM call")
                val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

                edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
                edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
                edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
            }

            val testModel = LLModel(
                provider = MockLLMProvider(),
                id = "test-llm-id",
                capabilities = emptyList(),
                contextLength = 1_000,
            )

            var runId = ""

            val agent = createAgent(
                promptId = promptId,
                model = testModel,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                strategy = strategy
            ) {
                install(Tracing) {
                    messageFilter = { message ->
                        if (message is AIAgentStartedEvent) { runId = message.runId }
                        message is BeforeLLMCallEvent || message is AfterLLMCallEvent
                    }
                    addMessageProcessor(writer)
                }
            }

            agent.run("")
            agent.close()

            val expectedPrompt = Prompt(
                messages = listOf(
                    systemMessage(systemPrompt),
                    userMessage(userPrompt),
                    assistantMessage(assistantPrompt),
                ),
                id = promptId,
            )

            val expectedResponse =
                assistantMessage(content = "Default test response")

            val expectedLogMessages = listOf(
                "${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + userMessage(
                            content = "Test LLM call prompt"
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
                "${BeforeLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, tools: [dummy])",
                "${AfterLLMCallEvent::class.simpleName} (run id: ${runId}, prompt: ${
                    expectedPrompt.copy(
                        messages = expectedPrompt.messages + listOf(
                            userMessage(content = "Test LLM call prompt"),
                            assistantMessage(content = "Default test response"),
                            userMessage(content = "Test LLM call with tools prompt")
                        )
                    ).traceString
                }, model: ${testModel.eventString}, responses: [${expectedResponse.traceString}])",
            )

            val actualMessages = writer.targetPath.readLines()

            assertEquals(expectedLogMessages.size, actualMessages.size)
            assertContentEquals(expectedLogMessages, actualMessages)
        }
    }
}
