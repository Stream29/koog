package ai.koog.agents.example.tmp

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolRegistry.Companion.invoke
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.tmp.ReportingLLMLLMClient.Event
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.coroutines.coroutineContext
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
        CoroutineScope(coroutineContext).launch {
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

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        CoroutineScope(coroutineContext).launch {
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
        return underlyingClient.executeStreaming(prompt, model)
    }
}

internal fun LLMClient.reportingTo(
    eventsChannel: Channel<Event>
) = ReportingLLMLLMClient(eventsChannel, this)

// API keys for testing
private val openAIApiKey: String get() = ApiKeyService.openAIApiKey
private val anthropicApiKey: String get() = ApiKeyService.anthropicApiKey

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
    data class Args(val path: String, val content: String) : Tool.Args

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
    data class Args(val path: String) : Tool.Args

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
    data class Args(val path: String) : Tool.Args

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
    data class Args(val path: String) : Tool.Args

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

private fun createTestOpenaiAnthropicAgent(
    eventsChannel: Channel<Event>,
    fs: MockFileSystem,
    eventHandlerConfig: EventHandlerConfig.() -> Unit,
    maxAgentIterations: Int
): AIAgent {
    val openAIClient = OpenAILLMClient(openAIApiKey).reportingTo(eventsChannel)
    val anthropicClient = AnthropicLLMClient(anthropicApiKey).reportingTo(eventsChannel)

    val executor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to openAIClient,
        LLMProvider.Anthropic to anthropicClient
    )

    val strategy = strategy("test") {
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
            edge(definePromptAnthropic forwardTo callLLM transformed { agentInput })
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
            edge(definePromptOpenAI forwardTo callLLM transformed { agentInput })
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
        agentConfig = AIAgentConfig(prompt("test") {}, OpenAIModels.Chat.GPT4o, maxAgentIterations),
        toolRegistry = tools,
    ) {
        install(Tracing) {
            addMessageProcessor(TestLogPrinter())
        }

        install(EventHandler, eventHandlerConfig)
    }
}

class TestLogPrinter : FeatureMessageProcessor() {
    override suspend fun processMessage(message: FeatureMessage) {
        println(message)
    }

    override suspend fun close() {
    }
}

fun main() {
    val eventsChannel = Channel<ReportingLLMLLMClient.Event>(Channel.UNLIMITED)
    val fs = MockFileSystem()
    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCall = { tool, arguments ->
            println(
                "Calling tool ${tool.name} with arguments ${
                    arguments.toString().lines().first().take(100)
                }"
            )
        }

        onAgentFinished = { _, _ ->
            eventsChannel.send(ReportingLLMLLMClient.Event.Termination)
        }
    }
    // Create the clients
    val agent = createTestOpenaiAnthropicAgent(eventsChannel, fs, eventHandlerConfig, maxAgentIterations = 42)

    runBlocking {
        val result = agent.runAndGetResult(
            "Generate me a project in Ktor that has a GET endpoint that returns the capital of France. Write a test"
        )
    }
}