package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.params.LLMParams

internal fun LLMParams.toDeepSeekParams(): DeepSeekParams {
    if (this is DeepSeekParams) return this
    return DeepSeekParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        includeThoughts = includeThoughts,
    )
}

/**
 * DeepSeek chat-completions parameters layered on top of [LLMParams].
 *
 * @property temperature Sampling temperature in [0.0, 2.0]. Higher ⇒ more random;
 *   lower ⇒ more deterministic. Adjust this **or** [topP], not both.
 * @property maxTokens Maximum number of tokens the model may generate for this response.
 * @property numberOfChoices Number of completions to generate for the prompt (cost scales with N).
 * @property speculation Provider-specific control for speculative decoding / draft acceleration.
 * @property schema JSON Schema to constrain model output (validated when supported).
 * @property toolChoice Controls if/which tool must be called (`none`/`auto`/`required`/specific).
 * @property user not used for DeepSeek
 * @property includeThoughts Request inclusion of model “thoughts”/reasoning traces (model-dependent).
 * @property thinkingBudget Soft cap on tokens spent on internal reasoning (reasoning models).
 * @property frequencyPenalty Number in [-2.0, 2.0]—penalizes frequent tokens to reduce repetition.
 * @property presencePenalty Number in [-2.0, 2.0]—encourages introduction of new tokens/topics.
 * @property logprobs Whether to include log-probabilities for output tokens.
 * @property stop Stop sequences (0–4 items); generation halts before any of these.
 * @property topLogprobs Number of top alternatives per position (0–20). Requires [logprobs] = true.
 * @property topP Nucleus sampling in (0.0, 1.0]; use **instead of** [temperature].
 */
public open class DeepSeekParams(
    temperature: Double? = null,
    maxTokens: Int? = null,
    numberOfChoices: Int? = null,
    speculation: String? = null,
    schema: Schema? = null,
    toolChoice: ToolChoice? = null,
    user: String? = null,
    includeThoughts: Boolean? = null,
    thinkingBudget: Int? = null,
    public val frequencyPenalty: Double? = null,
    public val presencePenalty: Double? = null,
    public val logprobs: Boolean? = null,
    public val stop: List<String>? = null,
    public val topLogprobs: Int? = null,
    public val topP: Double? = null,
) : LLMParams(
    temperature, maxTokens, numberOfChoices,
    speculation, schema, toolChoice,
    user, includeThoughts, thinkingBudget
) {
    init {
        require(topP == null || topP in 0.0..1.0) {
            "topP must be in (0.0, 1.0], but was $topP"
        }
        require(topLogprobs == null || topLogprobs in 0..20) {
            "topLogprobs must be in [0, 20], but was $topLogprobs"
        }
        require(frequencyPenalty == null || frequencyPenalty in -2.0..2.0) {
            "frequencyPenalty must be in [-2.0, 2.0], but was $frequencyPenalty"
        }
        require(presencePenalty == null || presencePenalty in -2.0..2.0) {
            "presencePenalty must be in [-2.0, 2.0], but was $presencePenalty"
        }
        // --- Log-probabilities ---
        if (topLogprobs != null) {
            require(topLogprobs in 0..20) {
                "topLogprobs must be in [0, 20], but was $topLogprobs"
            }
            require(logprobs == true) {
                "topLogprobs requires logprobs=true."
            }
        }

        // --- Stop sequences ---
        if (stop != null) {
            require(stop.isNotEmpty()) { "stop must not be empty when provided." }
            require(stop.size <= 4) { "stop supports at most 4 sequences, but was ${stop.size}" }
            require(stop.all { it.isNotBlank() }) { "stop sequences must not be blank." }
        }
    }

    /**
     * Creates a copy of this instance with the ability to modify any of its properties.
     */
    public fun copy(
        temperature: Double? = this.temperature,
        maxTokens: Int? = this.maxTokens,
        numberOfChoices: Int? = this.numberOfChoices,
        speculation: String? = this.speculation,
        schema: Schema? = this.schema,
        toolChoice: ToolChoice? = this.toolChoice,
        user: String? = this.user,
        includeThoughts: Boolean? = this.includeThoughts,
        thinkingBudget: Int? = this.thinkingBudget,
        frequencyPenalty: Double? = this.frequencyPenalty,
        presencePenalty: Double? = this.presencePenalty,
        logprobs: Boolean? = this.logprobs,
        stop: List<String>? = this.stop,
        topLogprobs: Int? = this.topLogprobs,
        topP: Double? = this.topP,
    ): DeepSeekParams = DeepSeekParams(
        temperature = temperature,
        maxTokens = maxTokens,
        numberOfChoices = numberOfChoices,
        speculation = speculation,
        schema = schema,
        toolChoice = toolChoice,
        user = user,
        includeThoughts = includeThoughts,
        thinkingBudget = thinkingBudget,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        logprobs = logprobs,
        stop = stop,
        topLogprobs = topLogprobs,
        topP = topP,
    )

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is DeepSeekParams -> false
        else -> temperature == other.temperature &&
            maxTokens == other.maxTokens &&
            numberOfChoices == other.numberOfChoices &&
            speculation == other.speculation &&
            schema == other.schema &&
            toolChoice == other.toolChoice &&
            user == other.user &&
            includeThoughts == other.includeThoughts &&
            thinkingBudget == other.thinkingBudget &&
            frequencyPenalty == other.frequencyPenalty &&
            presencePenalty == other.presencePenalty &&
            logprobs == other.logprobs &&
            stop == other.stop &&
            topLogprobs == other.topLogprobs &&
            topP == other.topP
    }

    override fun hashCode(): Int = listOf(
        temperature, maxTokens, numberOfChoices,
        speculation, schema, toolChoice,
        user, includeThoughts, thinkingBudget,
        frequencyPenalty, presencePenalty,
        logprobs, stop, topLogprobs, topP,
    ).fold(0) { acc, element ->
        31 * acc + (element?.hashCode() ?: 0)
    }

    override fun toString(): String = buildString {
        append("DeepSeekParams(")
        append("temperature=$temperature")
        append(", maxTokens=$maxTokens")
        append(", numberOfChoices=$numberOfChoices")
        append(", speculation=$speculation")
        append(", schema=$schema")
        append(", toolChoice=$toolChoice")
        append(", user=$user")
        append(", includeThoughts=$includeThoughts")
        append(", thinkingBudget=$thinkingBudget")
        append(", frequencyPenalty=$frequencyPenalty")
        append(", presencePenalty=$presencePenalty")
        append(", logprobs=$logprobs")
        append(", stop=$stop")
        append(", topLogprobs=$topLogprobs")
        append(", topP=$topP")
        append(")")
    }
}
