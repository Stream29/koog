package ai.koog.prompt.executor.llms.all

import ai.koog.client.anthropic.AnthropicLLMClient
import ai.koog.client.google.GoogleLLMClient
import ai.koog.client.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider

/**
 * Implementation of [MultiLLMPromptExecutor] that supports OpenAI, Anthropic, and Google providers.
 *
 * @param openAIClient The OpenAI client
 * @param anthropicClient The Anthropic client
 * @param googleClient The Google client
 */
public class DefaultMultiLLMPromptExecutor(
    openAIClient: OpenAILLMClient,
    anthropicClient: AnthropicLLMClient,
    googleClient: GoogleLLMClient,
) : MultiLLMPromptExecutor(
    LLMProvider.OpenAI to openAIClient,
    LLMProvider.Anthropic to anthropicClient,
    LLMProvider.Google to googleClient,
)
