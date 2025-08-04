package ai.koog.agents.features.debugger.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.feature.model.AIAgentFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionEndEvent
import ai.koog.agents.core.feature.model.AIAgentNodeExecutionStartEvent
import ai.koog.agents.core.feature.model.AIAgentStartedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyFinishedEvent
import ai.koog.agents.core.feature.model.AIAgentStrategyStartEvent
import ai.koog.agents.core.feature.model.AfterLLMCallEvent
import ai.koog.agents.core.feature.model.BeforeLLMCallEvent
import ai.koog.agents.core.feature.model.ToolCallEvent
import ai.koog.agents.core.feature.model.ToolCallResultEvent
import ai.koog.agents.core.feature.remote.client.FeatureMessageRemoteClient
import ai.koog.agents.core.feature.remote.client.config.AIAgentFeatureClientConnectionConfig
import ai.koog.agents.core.feature.remote.server.config.DefaultServerConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.debugger.EnvironmentVariablesReader
import ai.koog.agents.features.debugger.eventString
import ai.koog.agents.features.debugger.mock.ClientEventsCollector
import ai.koog.agents.features.debugger.mock.MockLLMProvider
import ai.koog.agents.features.debugger.mock.assistantMessage
import ai.koog.agents.features.debugger.mock.createAgent
import ai.koog.agents.features.debugger.mock.systemMessage
import ai.koog.agents.features.debugger.mock.testClock
import ai.koog.agents.features.debugger.mock.toolCallMessage
import ai.koog.agents.features.debugger.mock.toolResult
import ai.koog.agents.features.debugger.mock.userMessage
import ai.koog.agents.testing.network.NetUtil
import ai.koog.agents.testing.network.NetUtil.findAvailablePort
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import io.ktor.http.URLProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DebuggerTest {

    companion object {
        private val defaultClientServerTimeout = 30.seconds
        private const val HOST = "127.0.0.1"
    }

    @Test
    fun `test feature message remote writer collect events on agent run`() = runBlocking {
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
            messages = expectedPrompt.messages + userMessage(
                content = userPrompt
            )
        )

        val expectedLLMCallWithToolsPrompt = expectedPrompt.copy(
            messages = expectedPrompt.messages + listOf(
                userMessage(content = userPrompt),
                toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""),
                toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toMessage(clock = testClock)
            )
        )

        // Test Data
        val port = findAvailablePort()
        val clientConfig = AIAgentFeatureClientConnectionConfig(host = HOST, port = port, protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()
        val isServerStarted = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
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
                mockLLMToolCall(
                    tool = dummyTool,
                    args = DummyTool.Args("test"),
                    toolCallId = "0"
                ) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                promptId = promptId,
                userPrompt = userPrompt,
                systemPrompt = systemPrompt,
                assistantPrompt = assistantPrompt,
                toolRegistry = toolRegistry,
                promptExecutor = mockExecutor,
                model = testModel,
            ) {
                install(Debugger) {
                    setPort(port)

                    launch {
                        val messageProcessor = messageProcessor.single() as FeatureMessageRemoteWriter
                        val isServerStartedCheck = withTimeoutOrNull(defaultClientServerTimeout) {
                            messageProcessor.isOpen.first { it }
                        } != null

                        assertTrue(isServerStartedCheck, "Server did not start in time")
                        isServerStarted.complete(true)
                    }
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val clientEventsCollector =
                    ClientEventsCollector(client = client, expectedEventsCount = 18)

                val collectEventsJob =
                    clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                isServerStarted.await()
                client.connect()
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    AIAgentStartedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentStrategyStartEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt,
                        output = userPrompt
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-llm-call",
                        input = userPrompt
                    ),
                    BeforeLLMCallEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name)
                    ),
                    AfterLLMCallEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallPrompt,
                        model = testModel.eventString,
                        responses = listOf(toolCallMessage(dummyTool.name, content = """{"dummy":"test"}"""))
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-llm-call",
                        input = userPrompt,
                        output = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString()
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-tool-call",
                        input = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString()
                    ),
                    ToolCallEvent(
                        runId = clientEventsCollector.runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test"))
                    ),
                    ToolCallResultEvent(
                        runId = clientEventsCollector.runId,
                        toolCallId = "0",
                        toolName = dummyTool.name,
                        toolArgs = dummyTool.encodeArgs(DummyTool.Args("test")),
                        result = dummyTool.result
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-tool-call",
                        input = toolCallMessage(dummyTool.name, content = """{"dummy":"test"}""").toString(),
                        output = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString()
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString()
                    ),
                    BeforeLLMCallEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        tools = listOf(dummyTool.name)
                    ),
                    AfterLLMCallEvent(
                        runId = clientEventsCollector.runId,
                        prompt = expectedLLMCallWithToolsPrompt,
                        model = testModel.eventString,
                        responses = listOf(assistantMessage(mockResponse)),
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "test-node-llm-send-tool-result",
                        input = toolResult("0", dummyTool.name, dummyTool.result, dummyTool.result).toString(),
                        output = assistantMessage(mockResponse).toString()
                    ),
                    AIAgentStrategyFinishedEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        result = mockResponse
                    ),
                    AIAgentFinishedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        result = mockResponse
                    ),
                )

                assertEquals(
                    expectedEvents.size,
                    clientEventsCollector.collectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, clientEventsCollector.collectedEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test read port from parameter`() = runBlocking {
    }

    @Test
    @Disabled(
        """
        'KOOG_DEBUGGER_PORT' environment variable need to be set for a particular test via test framework.
        Currently, test framework that is used for Koog tests does not have ability to set env variables.
        Setting env variable in Gradle task does not work either, because there are tests that verify both 
        cases when env variable is set and when it is not set.
        Disable test for now. Need to be enabled when we can set env variables in tests.
    """
    )
    fun `test read port from KOOG_DEBUGGER_PORT env variable when not set by property`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        // Test Data
        val port = EnvironmentVariablesReader.getEnvironmentVariable("KOOG_DEBUGGER_PORT")
        assertNotNull(port, "'KOOG_DEBUGGER_PORT' env variable is not set")

        val clientConfig =
            AIAgentFeatureClientConnectionConfig(host = HOST, port = port.toInt(), protocol = URLProtocol.HTTP)

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                install(Debugger) {
                    // Do not set the port value.
                    // It should be read from the 'KOOG_DEBUGGER_PORT' env variable defined above.
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val clientEventsCollector = ClientEventsCollector(client = client, expectedEventsCount = 2)
                val collectEventsJob = clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connect()
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    AIAgentStartedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentStrategyStartEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt,
                        output = userPrompt
                    ),
                    AIAgentStrategyFinishedEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        result = userPrompt
                    ),
                    AIAgentFinishedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        result = userPrompt
                    ),
                )

                assertEquals(
                    expectedEvents.size,
                    clientEventsCollector.collectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, clientEventsCollector.collectedEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }

    @Test
    fun `test read default port when not set by property or env var`() = runBlocking {
        // Agent Config
        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val userPrompt = "Call the dummy tool with argument: test"

        // Test Data
        val port = EnvironmentVariablesReader.getEnvironmentVariable("KOOG_DEBUGGER_PORT")
        assertNull(port, "Expected 'KOOG_DEBUGGER_PORT' env variable is not set, but it exists with value: $port")

        // Check default port available
        val isDefaultPortAvailable = NetUtil.isPortAvailable(DefaultServerConnectionConfig.DEFAULT_PORT)
        assertTrue(isDefaultPortAvailable, "Default port ${DefaultServerConnectionConfig.DEFAULT_PORT} is not available")

        val clientConfig = AIAgentFeatureClientConnectionConfig(
            host = HOST,
            port = DefaultServerConnectionConfig.DEFAULT_PORT,
            protocol = URLProtocol.HTTP
        )

        val isClientFinished = CompletableDeferred<Boolean>()

        // Server
        val serverJob = launch {
            val strategy = strategy<String, String>(strategyName) {
                edge(nodeStart forwardTo nodeFinish)
            }

            createAgent(
                agentId = agentId,
                strategy = strategy,
                userPrompt = userPrompt,
            ) {
                install(Debugger) {
                    // Do not set the port value.
                    // It should take the default value when the 'KOOG_DEBUGGER_PORT' env variable is not defined.
                }
            }.use { agent ->
                agent.run(userPrompt)
                isClientFinished.await()
            }
        }

        // Client
        val clientJob = launch {
            FeatureMessageRemoteClient(connectionConfig = clientConfig, scope = this).use { client ->

                val clientEventsCollector = ClientEventsCollector(client = client, expectedEventsCount = 6)
                val collectEventsJob = clientEventsCollector.startCollectEvents(coroutineScope = this@launch)

                client.connect()
                collectEventsJob.join()

                // Correct run id will be set after the 'collect events job' is finished.
                val expectedEvents = listOf(
                    AIAgentStartedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentStrategyStartEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName
                    ),
                    AIAgentNodeExecutionStartEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt
                    ),
                    AIAgentNodeExecutionEndEvent(
                        runId = clientEventsCollector.runId,
                        nodeName = "__start__",
                        input = userPrompt,
                        output = userPrompt
                    ),
                    AIAgentStrategyFinishedEvent(
                        runId = clientEventsCollector.runId,
                        strategyName = strategyName,
                        result = userPrompt
                    ),
                    AIAgentFinishedEvent(
                        agentId = agentId,
                        runId = clientEventsCollector.runId,
                        result = userPrompt
                    ),
                )

                assertEquals(
                    expectedEvents.size,
                    clientEventsCollector.collectedEvents.size,
                    "expectedEventsCount variable in the test need to be updated"
                )
                assertContentEquals(expectedEvents, clientEventsCollector.collectedEvents)

                isClientFinished.complete(true)
            }
        }

        val isFinishedOrNull = withTimeoutOrNull(defaultClientServerTimeout) {
            listOf(clientJob, serverJob).joinAll()
        }

        assertNotNull(isFinishedOrNull, "Client or server did not finish in time")
    }
}
