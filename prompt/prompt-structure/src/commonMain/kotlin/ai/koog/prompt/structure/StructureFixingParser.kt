package ai.koog.prompt.structure

import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException

/**
 * Helper fixing parser for handling malformed structured data that uses LLMs to attempt to
 * correct any errors in the provided content to produce valid structured outputs.
 *
 * @property fixingModel LLM to use for processing and attempting to fix format.
 * @property retries Number of attempts to fix the structure before giving up.
 * @property fixingPrompt Prompt explaining to LLM how to fix a given structure.
 */
public class StructureFixingParser(
    public val fixingModel: LLModel,
    public val retries: Int,
    private val fixingPrompt: (
        builder: PromptBuilder,
        content: String,
        structure: StructuredData<*, *>,
        exception: SerializationException
    ) -> PromptBuilder = ::defaultFixingPrompt,
) {

    init {
        require(retries > 0) { "Retries must be greater than 0" }
    }

    /**
     * Parses the given content string into an instance of the specified type using the provided structured data schema.
     * If the initial parsing fails, attempts to fix the content based on the error and retries parsing.
     *
     * @param executor Executor to preform requests to [fixingModel]
     * @param structure The structured data schema and serializer to use for parsing the content.
     * @param content The string content to parse into the structured data type.
     * @throws SerializationException If parsing fails both initially and after attempting to fix the content.
     */
    public suspend fun <T> parse(executor: PromptExecutor, structure: StructuredData<T, *>, content: String): T {
        var currentContent = content
        var currentException: SerializationException? = null

        repeat(retries) { attempt ->
            try {
                logger.debug { "$attempt/$retries: Try to parse LLM response content: <$content>" }

                return structure.parse(currentContent)
            } catch (exception: SerializationException) {
                logger.warn(exception) { "Failed to parse structure from content: <$content>" }

                currentException = exception
                currentContent = executeFixStructure(executor, content, structure, exception)
            }
        }

        throw LLMStructuredParsingError("Unable to parse structure after $retries retries", currentException)
    }

    private suspend fun <T> executeFixStructure(
        executor: PromptExecutor,
        content: String,
        structure: StructuredData<T, *>,
        exception: SerializationException,
    ): String {
        val prompt = prompt(
            "structure-fixing",
            LLMParams(
                schema = if (structure.schema.capability in fixingModel.capabilities) {
                    structure.schema
                } else {
                    null
                }
            )
        ) {
            fixingPrompt(this, content, structure, exception)
        }

        val response = executor.execute(prompt = prompt, model = fixingModel).single()
        require(response is Message.Assistant) { "Response for fixing structure must be an assistant message, got ${response::class.simpleName} instead" }

        return response.content
    }

    /**
     * Companion object providing some utility functions.
     */
    public companion object {
        private val logger = KotlinLogging.logger { }

        /**
         * Default prompt explaining how to fix a malformed structure.
         */
        public fun defaultFixingPrompt(
            builder: PromptBuilder,
            content: String,
            structure: StructuredData<*, *>,
            exception: SerializationException
        ): PromptBuilder = builder.apply {
            system {
                markdown {
                    +"You are agent responsible for converting incorrectly generated LLM Structured Output into valid format that adheres to the given schema."
                    +"Your sole responsibility is to fix the generated content to conform to the given schema."
                    newline()

                    h2("PROCESS")
                    bulleted {
                        item("Evaluate what parts are incorrect and fix them.")
                        item("Drop unknown fields and come-up with values for missing fields based on semantics.")
                        item("Carefully check the types of the fields and fix if any are incorrect.")
                        item("Utilize the provided exception to determine the possible error, but do not forget about other possible mistakes.")
                    }

                    h2("KEY PRINCIPLES")
                    bulleted {
                        item("You MUST stick to the original data, make as few changes as possible to convert it into valid schema.")
                        item("Do not drop, alter or change any semantic data unless it is necessary to fit into schema.")
                    }

                    h2("RESULT")
                    +"Provide ONLY the fixed structured data, WITHOUT any comments and backticks."

                    h2("DEFINITION")
                    structure.definition(this)
                }
            }
            user {
                markdown {
                    h2("EXCEPTION")
                    codeblock(exception.message ?: "Unknown exception")

                    h2("CONTENT")
                    codeblock(content)
                }
            }
        }
    }
}
