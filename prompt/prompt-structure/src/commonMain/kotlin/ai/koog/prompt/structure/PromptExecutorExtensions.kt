package ai.koog.prompt.structure

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * Represents a container for structured data parsed from response message.
 *
 * This class is designed to encapsulate both the parsed structured output and the original raw
 * text as returned from a processing step, such as a language model execution.
 *
 * @param T The type of the structured data contained within this response.
 * @property structure The parsed structured data corresponding to the specific schema.
 * @property message The original assistant message from which the structure was parsed.
 */
public data class StructuredResponse<T>(val structure: T, val message: Message.Assistant)

/**
 * Configures structured output behavior.
 * Defines which structures in which modes should be used for each provider when requesting a structured output.
 *
 * @property default Fallback [StructuredOutput] to be used when there's no suitable structure found in [byProvider]
 * for a requested [LLMProvider]. Defaults to `null`, meaning structured output would fail with error in such a case.
 *
 * @property byProvider A map matching [LLMProvider] to compatible [StructuredOutput] definitions. Each provider may
 * require different schema formats. E.g. for [JsonStructuredData] this means you have to use the appropriate
 * [JsonSchemaGenerator] implementation for each provider for [StructuredOutput.Native], or fallback to [StructuredOutput.Manual]
 *
 * @property fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 */
public data class StructuredOutputConfig<T>(
    public val default: StructuredOutput<T>? = null,
    public val byProvider: Map<LLMProvider, StructuredOutput<T>> = emptyMap(),
    public val fixingParser: StructureFixingParser? = null
)

/**
 * Defines how structured outputs should be generated.
 *
 * Can be [StructuredOutput.Manual] or [StructuredOutput.Native]
 *
 * @param T The type of structured data.
 */
public sealed interface StructuredOutput<T> {
    /**
     * The definition of a structure.
     */
    public val structure: StructuredData<T, *>

    /**
     * Instructs the model to produce structured output through explicit prompting.
     *
     * Uses an additional user message containing [StructuredData.definition] to guide
     * the model in generating correctly formatted responses.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Manual<T>(override val structure: StructuredData<T, *>) : StructuredOutput<T>

    /**
     * Leverages a model's built-in structured output capabilities.
     *
     * Uses [StructuredData.schema] to define the expected response format through the model's
     * native structured output functionality.
     *
     * Note: [StructuredData.examples] are not used with this mode, only the schema is sent via parameters.
     *
     * @property structure The structure definition to be used in output generation.
     */
    public data class Native<T>(override val structure: StructuredData<T, *>) : StructuredOutput<T>
}

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * **Note**: While many language models advertise support for structured output via JSON schema,
 * the actual level of support varies between models and even between versions
 * of the same model. Some models may produce malformed outputs or deviate from
 * the schema in subtle ways, especially with complex structures like polymorphic types.
 *
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param config A configuration defining structures and behavior.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    config: StructuredOutputConfig<T>,
): Result<StructuredResponse<T>> {
    val mode = config.byProvider[model.provider]
        ?: config.default
        ?: throw IllegalArgumentException("No structure found for provider ${model.provider}")

    val (structure: StructuredData<T, *>, updatedPrompt: Prompt) = when (mode) {
        // Don't set schema parameter in prompt and coerce the model manually with user message to provide a structured response.
        is StructuredOutput.Manual -> {
            mode.structure to prompt(prompt) {
                user {
                    markdown {
                        StructuredOutputPrompts.outputInstructionPrompt(this, mode.structure)
                    }
                }
            }
        }

        // Rely on built-in model capabilities to provide structured response.
        is StructuredOutput.Native -> {
            mode.structure to prompt.withUpdatedParams { schema = mode.structure.schema }
        }
    }

    val response = this.execute(prompt = updatedPrompt, model = model).single()

    return runCatching {
        require(response is Message.Assistant) { "Response for structured output must be an assistant message, got ${response::class.simpleName} instead" }

        // Use fixingParser if provided, otherwise parse directly
        val structureResponse = config.fixingParser
            ?.parse(this, structure, response.content)
            ?: structure.parse(response.content)

        StructuredResponse(
            structure = structureResponse,
            message = response
        )
    }
}

/**
 * Registered mapping of providers to their respective known simple JSON schema format generators.
 * The registration is supposed to be done by the LLM clients when they are loaded, to communicate their custom formats.
 *
 * Used to attempt to get a proper generator implicitly in the simple version of [executeStructured] (that does not accept [StructuredOutput] explicitly)
 * to attempt to generate an appropriate schema for the passed [KType].
 */
@InternalStructuredOutputApi
public val RegisteredBasicJsonSchemaGenerators: MutableMap<LLMProvider, BasicJsonSchemaGenerator> = mutableMapOf()

/**
 * Registered mapping of providers to their respective known full JSON schema format generators.
 * The registration is supposed to be done by the LLM clients on their initialization, to communicate their custom formats.
 *
 * Used to attempt to get a proper generator implicitly in the simple version of [executeStructured] (that does not accept [StructuredOutput] explicitly)
 * to attempt to generate an appropriate schema for the passed [KType].
 */
@InternalStructuredOutputApi
public val RegisteredStandardJsonSchemaGenerators: MutableMap<LLMProvider, StandardJsonSchemaGenerator> = mutableMapOf()

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * This is a simple version of the full `executeStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined [model] capabilities.
 *
 * For example, it chooses which JSON schema to use (simple or full) and with which mode (native or manual).
 *
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param serializer Serializer for the requested structure type.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
@OptIn(InternalStructuredOutputApi::class)
public suspend fun <T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    serializer: KSerializer<T>,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): Result<StructuredResponse<T>> {
    @Suppress("UNCHECKED_CAST")
    val id = serializer.descriptor.serialName.substringAfterLast(".")

    val structuredOutput = when {
        LLMCapability.Schema.JSON.Standard in model.capabilities -> StructuredOutput.Native(
            JsonStructuredData.createJsonStructure(
                id = id,
                serializer = serializer,
                schemaGenerator = RegisteredStandardJsonSchemaGenerators[model.provider] ?: StandardJsonSchemaGenerator
            )
        )

        LLMCapability.Schema.JSON.Basic in model.capabilities -> StructuredOutput.Native(
            JsonStructuredData.createJsonStructure(
                id = id,
                serializer = serializer,
                schemaGenerator = RegisteredBasicJsonSchemaGenerators[model.provider] ?: BasicJsonSchemaGenerator
            )
        )

        else -> StructuredOutput.Manual(
            JsonStructuredData.createJsonStructure(
                id = id,
                serializer = serializer,
                schemaGenerator = StandardJsonSchemaGenerator,
                examples = examples,
            )
        )
    }

    return executeStructured(
        prompt = prompt,
        model = model,
        config = StructuredOutputConfig(
            default = structuredOutput,
            fixingParser = fixingParser,
        )
    )
}

/**
 * Executes a prompt with structured output, enhancing it with schema instructions or native structured output
 * parameter, and parses the response into the defined structure.
 *
 * This is a simple version of the full `executeStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined [model] capabilities.
 *
 * For example, it chooses which JSON schema to use (simple or full) and with which mode (native or manual).
 *
 * @param T The structure to request.
 * @param prompt The prompt to be executed.
 * @param model LLM to execute requests.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 *
 * @return [kotlin.Result] with parsed [StructuredResponse] or error.
 */
public suspend inline fun <reified T> PromptExecutor.executeStructured(
    prompt: Prompt,
    model: LLModel,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null,
): Result<StructuredResponse<T>> {
    return executeStructured(
        prompt = prompt,
        model = model,
        serializer = serializer<T>(),
        examples = examples,
        fixingParser = fixingParser,
    )
}
