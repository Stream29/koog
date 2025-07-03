package ai.koog.agents.features.opentelemetry.mock

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

class MockLLMExecutor(val clock: Clock) : PromptExecutor {

    override suspend fun execute(prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>): List<Message.Response> {
        return listOf(handlePrompt(prompt, tools))
    }

    override suspend fun executeStreaming(prompt: Prompt, model: LLModel): Flow<String> {
        return flow {
            emit(handlePrompt(prompt, emptyList()).content)
        }
    }

    private fun handlePrompt(prompt: Prompt, tools: List<ToolDescriptor>): Message.Response {
        // For a compression test, return a summary
        if (prompt.messages.any { it.content.contains("Summarize all the main achievements") }) {
            return Message.Assistant(
                "Here's a summary of the conversation: Test user asked questions and received responses.",
                ResponseMetaInfo.Companion.create(clock)
            )
        }

        // If tools are available, return a tool call
        if (tools.isNotEmpty()) {
            val tool = tools.first()
            return Message.Tool.Call(
                id = "test-tool-call-id",
                tool = tool.name,
                content = """{"dummy": "test value"}""",
                metaInfo = ResponseMetaInfo.Companion.create(clock)
            )
        }

        return Message.Assistant("Default test response", ResponseMetaInfo.Companion.create(clock))
    }
}