package ai.koog.integration.tests

import ai.koog.integration.tests.ReportingLLMLLMClient.Event
import ai.koog.integration.tests.utils.TestUtils.readTestAnthropicKeyFromEnv
import ai.koog.integration.tests.utils.TestUtils.readTestOpenAIKeyFromEnv
import ai.koog.integration.tests.utils.TestLogPrinter
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentException
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.*
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

internal class ReportingLLMLLMClient(
    private val eventsChannel: Channel<Event>,
    private val underlyingClient
    : LLMClient
) : LLMClient {
    sealed interface Event {
        data class Message(
            val llmClient: String,
            val method: String,
            val prompt: Prompt,
            val tools: List<String>,
            val model: LLModel
        ) : Event

        data object Termination : Event
    }

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        CoroutineScope(currentCoroutineContext()).launch {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = tools.map { it.name },
                    model = model
                )
            )
        }
        return underlyingClient.execute(prompt, model, tools)
    }

    override fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> = flow {
        coroutineScope {
            eventsChannel.send(
                Event.Message(
                    llmClient = underlyingClient::class.simpleName ?: "null",
                    method = "execute",
                    prompt = prompt,
                    tools = emptyList(),
                    model = model
                )
            )
        }
        underlyingClient.executeStreaming(prompt, model)
            .collect(this)
    }
}

internal fun LLMClient.reportingTo(
    eventsChannel: Channel<Event>
) = ReportingLLMLLMClient(eventsChannel, this)

class AIAgentMultipleLLMIntegrationTest {

    companion object {
        private lateinit var testResourcesDir: File

        @JvmStatic
        @BeforeAll
        fun setup() {
            testResourcesDir = File("src/jvmTest/resources/media")
            testResourcesDir.mkdirs()
            assertTrue(testResourcesDir.exists(), "Test resources directory should exist")
        }

        internal val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { _, _, _, _ ->
                eventsChannel.send(Event.Termination)
            }
        }

        @JvmStatic
        fun modelsWithVisionCapability(): Stream<Arguments> {
            return Models.modelsWithVisionCapability()
        }
    }

    // API keys for testing
    private val openAIApiKey: String get() = readTestOpenAIKeyFromEnv()
    private val anthropicApiKey: String get() = readTestAnthropicKeyFromEnv()

    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    object CalculatorTool : Tool<CalculatorTool.Args, ToolResult.Number>() {
        @Serializable
        data class Args(val operation: CalculatorOperation, val a: Int, val b: Int) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        override suspend fun execute(args: Args): ToolResult.Number = when (args.operation) {
            CalculatorOperation.ADD -> args.a + args.b
            CalculatorOperation.SUBTRACT -> args.a - args.b
            CalculatorOperation.MULTIPLY -> args.a * args.b
            CalculatorOperation.DIVIDE -> args.a / args.b
        }.let(ToolResult::Number)
    }

    sealed interface OperationResult<T> {
        class Success<T>(val result: T) : OperationResult<T>
        class Failure<T>(val error: String) : OperationResult<T>
    }

    class MockFileSystem {
        private val fileContents: MutableMap<String, String> = mutableMapOf()

        fun create(path: String, content: String): OperationResult<Unit> {
            if (path in fileContents) return OperationResult.Failure("File already exists")
            fileContents[path] = content
            return OperationResult.Success(Unit)
        }

        fun delete(path: String): OperationResult<Unit> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            fileContents.remove(path)
            return OperationResult.Success(Unit)
        }

        fun read(path: String): OperationResult<String> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            return OperationResult.Success(fileContents[path]!!)
        }

        fun ls(path: String): OperationResult<List<String>> {
            if (path in fileContents) {
                return OperationResult.Failure("Path $path points to a file, but not a directory!")
            }
            val matchingFiles = fileContents
                .filter { (filePath, _) -> filePath.startsWith(path) }
                .map { (filePath, _) -> filePath }

            if (matchingFiles.isEmpty()) {
                return OperationResult.Failure("No files in the directory. Directory doesn't exist or is empty.")
            }
            return OperationResult.Success(matchingFiles)
        }

        fun fileCount(): Int = fileContents.size
    }

    class CreateFile(private val fs: MockFileSystem) : Tool<CreateFile.Args, CreateFile.Result>() {
        @Serializable
        data class Args(val path: String, val content: String) : ToolArgs

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer() = serializer()
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "create_file",
            description = "Create a file and writes the given text content to it",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path to create the file",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "content",
                    description = "The content to create the file",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.create(args.path, args.content)
            return when (res) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>() {
        @Serializable
        data class Args(val path: String) : ToolArgs

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null
        ) : ToolResult {
            override fun toStringDefault(): String = "successful: $successful, message: \"$message\""
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "delete_file",
            description = "Deletes a file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the file to be deleted",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.delete(args.path)
            return when (res) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ReadFile(private val fs: MockFileSystem) : Tool<ReadFile.Args, ReadFile.Result>() {
        @Serializable
        data class Args(val path: String) : ToolArgs

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val content: String? = null
        ) : ToolResult.JSONSerializable<Result> {
            override fun getSerializer() = serializer()
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read_file",
            description = "Reads a file",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the file to read",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.read(args.path)
            return when (res) {
                is OperationResult.Success<String> -> Result(successful = true, content = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ListFiles(private val fs: MockFileSystem) : Tool<ListFiles.Args, ListFiles.Result>() {
        @Serializable
        data class Args(val path: String) : ToolArgs

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val children: List<String>? = null
        ) : ToolResult {
            override fun toStringDefault(): String =
                "successful: $successful, message: \"$message\", children: ${children?.joinToString()}"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "list_files",
            description = "List all files inside the given path of the directory",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = "The path of the directory",
                    type = ToolParameterType.String
                )
            )
        )

        override suspend fun execute(args: Args): Result {
            val res = fs.ls(args.path)
            return when (res) {
                is OperationResult.Success<List<String>> -> Result(successful = true, children = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun createTestOpenaiAnthropicAgent(
        eventsChannel: Channel<Event>,
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int,
        prompt: Prompt = prompt("test") {},
    ): AIAgent<String, String> {
        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)

        val executor = MultiLLMPromptExecutor(
            LLMProvider.OpenAI to openAIClient,
            LLMProvider.Anthropic to anthropicClient
        )

        val strategy = strategy<String, String>("test") {
            val anthropicSubgraph by subgraph<String, Unit>("anthropic") {
                val definePromptAnthropic by node<Unit, Unit> {
                    llm.writeSession {
                        model = AnthropicModels.Sonnet_3_7
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system(
                                    "You are a helpful assistant. You need to solve my task. " +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE " +
                                            "WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptAnthropic transformed {})
                edge(definePromptAnthropic forwardTo callLLM transformed { agentInput<String>() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val openaiSubgraph by subgraph("openai") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test", params = LLMParams(toolChoice = LLMParams.ToolChoice.Auto)) {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! " +
                                            "ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE " +
                                            "AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput<String>() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val compressHistoryNode by nodeLLMCompressHistory<Unit>("compress_history")

            nodeStart then anthropicSubgraph then compressHistoryNode then openaiSubgraph then nodeFinish
        }

        val tools = ToolRegistry {
            tool(CreateFile(fs))
            tool(DeleteFile(fs))
            tool(ReadFile(fs))
            tool(ListFiles(fs))
        }

        // Create the agent
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt, OpenAIModels.Chat.GPT4o, maxAgentIterations),
            toolRegistry = tools,
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler, eventHandlerConfig)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun createTestOpenaiAgent(
        eventsChannel: Channel<Event>,
        fs: MockFileSystem,
        eventHandlerConfig: EventHandlerConfig.() -> Unit,
        maxAgentIterations: Int,
        prompt: Prompt = prompt("test") {},
    ): AIAgent<String, String> {
        val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
        val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)

        // Create the executor
        val executor = //grazieExecutor
            MultiLLMPromptExecutor(
                LLMProvider.OpenAI to openAIClient,
                LLMProvider.Anthropic to anthropicClient
            )

        // Create a simple agent strategy
        val strategy = strategy<String, String>("test") {
            val openaiSubgraphFirst by subgraph<String, Unit>("openai0") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }


                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI transformed {})
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput<String>() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true } transformed {})
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val openaiSubgraphSecond by subgraph("openai1") {
                val definePromptOpenAI by node<Unit, Unit> {
                    llm.writeSession {
                        model = OpenAIModels.Chat.GPT4o
                        rewritePrompt {
                            prompt("test") {
                                system(
                                    "You are a helpful assistant. You need to verify that the task is solved correctly. " +
                                            "Please analyze the whole produced solution, and check that it is valid." +
                                            "Write concise verification result." +
                                            "CALL TOOLS!!! DO NOT SEND MESSAGES!!!!! ONLY SEND THE FINAL MESSAGE WHEN YOU ARE FINISHED AND EVERYTING IS DONE AFTER CALLING THE TOOLS!"
                                )
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()


                edge(nodeStart forwardTo definePromptOpenAI)
                edge(definePromptOpenAI forwardTo callLLM transformed { agentInput<String>() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val compressHistoryNode by nodeLLMCompressHistory<Unit>("compress_history")

            nodeStart then openaiSubgraphFirst then compressHistoryNode then openaiSubgraphSecond then nodeFinish
        }

        val tools = ToolRegistry {
            tool(CreateFile(fs))
            tool(DeleteFile(fs))
            tool(ReadFile(fs))
            tool(ListFiles(fs))
        }

        // Create the agent
        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt, OpenAIModels.Chat.GPT4o, maxAgentIterations),
            toolRegistry = tools,
        ) {
            install(Tracing) {
                addMessageProcessor(TestLogPrinter())
            }

            install(EventHandler, eventHandlerConfig)
        }
    }

    @Test
    fun integration_testAIAgentOpenAIAndAnthropic() = runTest(timeout = 600.seconds) {
        // Create the clients
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { agentId, sessionId, strategyName, result ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

        val result = agent.run(
            "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
        )

        assertNotNull(result)

        assertTrue(
            fs.fileCount() > 0,
            "Agent must have created at least one file"
        )

        val messages = mutableListOf<Event.Message>()
        for (msg in eventsChannel) {
            if (msg is Event.Message) messages.add(msg)
            else break
        }

        assertTrue(
            messages.any { it.llmClient == "AnthropicLLMClient" },
            "At least one message must be delegated to Anthropic client"
        )

        assertTrue(
            messages.any { it.llmClient == "OpenAILLMClient" },
            "At least one message must be delegated to OpenAI client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "AnthropicLLMClient" }
                .all { it.model.provider == LLMProvider.Anthropic },
            "All prompts with Anthropic model must be delegated to Anthropic client"
        )

        assertTrue(
            messages
                .filter { it.llmClient == "OpenAILLMClient" }
                .all { it.model.provider == LLMProvider.OpenAI },
            "All prompts with OpenAI model must be delegated to OpenAI client"
        )
    }

    @Test
    fun integration_testTerminationOnIterationsLimitExhaustion() = runTest(timeout = 600.seconds) {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        var errorMessage: String? = null
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { agentId, sessionId, strategyName, result ->
                eventsChannel.send(Event.Termination)
            }
        }
        val steps = 10
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = steps)

        try {
            val result = agent.run(
                "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
            )
            assertNull(result)
        } catch (e: AIAgentException) {
            errorMessage = e.message
        } finally {
            assertEquals(
                "AI Agent has run into a problem: Agent couldn't finish in given number of steps ($steps). " +
                        "Please, consider increasing `maxAgentIterations` value in agent's configuration",
                errorMessage
            )
        }
    }

    @Test
    fun integration_testAnthropicAgent() = runTest {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { agentId, sessionId, strategyName, result ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)
        val result = agent.run(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Test
    fun integration_testOpenAIAnthropicAgentWithTools() = runTest(timeout = 300.seconds) {
        val eventsChannel = Channel<Event>(Channel.UNLIMITED)
        val fs = MockFileSystem()
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCall { tool, arguments ->
                println(
                    "Calling tool ${tool.name} with arguments ${
                        arguments.toString().lines().first().take(100)
                    }"
                )
            }

            onAgentFinished { agentId, sessionId, strategyName, result ->
                eventsChannel.send(Event.Termination)
            }
        }
        val agent = createTestOpenaiAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

        val result = agent.run(
            "Name me a capital of France"
        )

        assertNotNull(result)
    }

    @Test
    fun integration_testAnthropicAgentEnumSerialization() {
        runBlocking {
            val agent = AIAgent(
                executor = simpleAnthropicExecutor(anthropicApiKey),
                llmModel = AnthropicModels.Sonnet_3_7,
                systemPrompt = "You are a calculator with access to the calculator tools. Please call tools!!!",
                toolRegistry = ToolRegistry {
                    tool(CalculatorTool)
                },
                installFeatures = {
                    install(EventHandler) {
                        onAgentRunError { _, _, e ->
                            println("error: ${e.javaClass.simpleName}(${e.message})\n${e.stackTraceToString()}")
                            true
                        }
                        onToolCall { tool, arguments ->
                            println(
                                "Calling tool ${tool.name} with arguments ${
                                    arguments.toString().lines().first().take(100)
                                }"
                            )
                        }
                    }
                }
            )

            val result = agent.run("calculate 10 plus 15, and then subtract 8")
            println("result = $result")
            assertNotNull(result)
            assertContains(result, "17")
        }
    }

    // TODO remove when APIs for non-encoded images are ready
    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapability(model: LLModel) = runTest(timeout = 120.seconds) {
        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val imageBytes = imageFile.readBytes()
        val base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes)

        withRetry {
            val agent = when (model.provider) {
                is LLMProvider.Anthropic -> createTestOpenaiAnthropicAgent(
                    eventsChannel,
                    fs,
                    eventHandlerConfig,
                    maxAgentIterations = 20,
                )

                else -> createTestOpenaiAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 20)
            }

            val result = agent.run(
                """
            I'm sending you an image encoded in base64 format.

            data:image/png,$base64Image

            Please analyze this image and identify the image format if possible.
            """
            )

            assertNotNull(result, "Result should not be null")
            assertTrue(result.isNotBlank(), "Result should not be empty or blank")
            assertTrue(result.length > 20, "Result should contain more than 20 characters")

            val resultLowerCase = result.lowercase()
            assertFalse(
                resultLowerCase.contains("error processing"),
                "Result should not contain error messages"
            )
            assertFalse(
                resultLowerCase.contains("unable to process"),
                "Result should not indicate inability to process"
            )
            assertFalse(
                resultLowerCase.contains("cannot process"),
                "Result should not indicate inability to process"
            )
        }
    }

    @Ignore("The functionality is not ready yet")
    @ParameterizedTest
    @MethodSource("modelsWithVisionCapability")
    fun integration_testAgentWithImageCapabilityPrompt(model: LLModel) = runTest(timeout = 120.seconds) {
        val imageFile = File(testResourcesDir, "test.png")
        assertTrue(imageFile.exists(), "Image test file should exist")

        val prompt = prompt("example-prompt") {
            system("You are a professional helpful assistant.")

            user {
                markdown {
                    +"I'm sending you an image."
                    br()
                    +"Please analyze this image and identify the image format if possible."
                }

                attachments {
                    image(imageFile.absolutePath)
                }
            }
        }

        val agent = when (model.provider) {
            is LLMProvider.Anthropic -> createTestOpenaiAnthropicAgent(
                eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 20, prompt = prompt
            )

            else -> createTestOpenaiAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 20)
        }


        val result = agent.run("Hi! Please analyse my image.")

        assertNotNull(result, "Result should not be null")
        assertTrue(result.isNotBlank(), "Result should not be empty or blank")
        assertTrue(result.length > 20, "Result should contain more than 20 characters")

        val resultLowerCase = result.lowercase()
        assertFalse(resultLowerCase.contains("error processing"), "Result should not contain error messages")
        assertFalse(
            resultLowerCase.contains("unable to process"),
            "Result should not indicate inability to process"
        )
        assertFalse(
            resultLowerCase.contains("cannot process"),
            "Result should not indicate inability to process"
        )
    }
}
