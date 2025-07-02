package ai.koog.agents.features.opentelemetry.feature

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
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> {
        return listOf(handlePrompt(prompt))
    }

    override suspend fun executeStreaming(
        prompt: Prompt,
        model: LLModel
    ): Flow<String> {
        return flow {
            emit(handlePrompt(prompt).content)
        }
    }

    private fun handlePrompt(prompt: Prompt): Message.Response {
        return Message.Assistant(
            content = "Default test response",
            metaInfo = ResponseMetaInfo.create(clock)
        )
    }
}
