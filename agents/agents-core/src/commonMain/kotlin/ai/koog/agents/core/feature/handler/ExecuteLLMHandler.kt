package ai.koog.agents.core.feature.handler

/**
 * A handler responsible for managing the execution flow of a Large Language Model (LLM) call.
 * It allows customization of logic to be executed before and after the LLM is called.
 */
public class ExecuteLLMHandler {

    /**
     * A handler that is invoked before making a call to the Language Learning Model (LLM).
     *
     * This handler enables customization or preprocessing steps to be applied before querying the model.
     * It accepts the prompt, a list of tools, the model, and a session UUID as inputs, allowing
     * users to define specific logic or modifications to these inputs before the call is made.
     */
    public var beforeLLMCallHandler: BeforeLLMCallHandler =
        BeforeLLMCallHandler { _ -> }

    /**
     * A handler invoked after a call to a language model (LLM) is executed.
     *
     * This variable represents a custom implementation of the `AfterLLMCallHandler` functional interface,
     * allowing post-processing or custom logic to be performed once the LLM has returned a response.
     *
     * The handler receives various pieces of information about the LLM call, including the original prompt,
     * the tools used, the model invoked, the responses returned by the model, and a unique session identifier.
     *
     * Customize this handler to implement specific behavior required immediately after LLM processing.
     */
    public var afterLLMCallHandler: AfterLLMCallHandler =
        AfterLLMCallHandler { _ -> }

    /**
     * A handler for managing the initiation of a streaming session for a language learning model (LLM).
     *
     * This variable allows customization of the logic executed when an LLM streaming session starts.
     * It leverages the `StartLLMStreamingHandler` functional interface, which receives a context
     * containing relevant details about the streaming session.
     *
     * Modify this handler to implement specific behaviors or processes required at the start
     * of an LLM streaming interaction.
     */
    public var startLLMStreamingHandler: StartLLMStreamingHandler =
        StartLLMStreamingHandler { _ -> }

    /**
     * A handler invoked before executing an operation that involves multiple choices.
     *
     * This variable represents an implementation of the `BeforeExecuteMultipleChoicesHandler`
     * functional interface. The handler allows for custom logic to be executed
     * prior to an operation involving multiple choices. It provides a context
     * containing detailed information about the operation, such as the session,
     * prompt, language model, and available tools.
     *
     * Use this handler to define any preparatory work or transformations
     * needed before the execution of such operations.
     */
    public var beforeExecuteMultipleChoices: BeforeExecuteMultipleChoicesHandler =
        BeforeExecuteMultipleChoicesHandler { _ -> }

    /**
     * A handler invoked after the execution of multiple-choice operations
     * within a language model interaction.
     *
     * This property represents an implementation of the `AfterExecuteMultipleChoicesHandler`
     * functional interface. It is used to execute custom logic after multiple-choice
     * operations are processed, providing access to the context of the operation.
     *
     * Custom implementations can be defined to handle specific behavior or
     * postprocessing requirements after the interaction is completed.
     */
    public var afterExecuteMultipleChoices: AfterExecuteMultipleChoicesHandler =
        AfterExecuteMultipleChoicesHandler { _ -> }
}

/**
 * A functional interface implemented to handle logic that occurs before invoking a large language model (LLM).
 * It allows preprocessing steps or validation based on the provided prompt, available tools, targeted LLM model,
 * and a unique session identifier.
 *
 * This can be particularly useful for custom input manipulation, logging, validation, or applying
 * configurations to the LLM request based on external context.
 */
public fun interface BeforeLLMCallHandler {
    /**
     * Handles a language model interaction by processing the given prompt, tools, model, and sess
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: BeforeLLMCallContext)
}

/**
 * Represents a functional interface for handling operations or logic that should occur after a call
 * to a large language model (LLM) is made. The implementation of this interface provides a mechanism
 * to perform custom logic or processing based on the provided inputs, such as the prompt, tools,
 * model, and generated responses.
 */
public fun interface AfterLLMCallHandler {
    /**
     * Handles the post-processing of a prompt and its associated data after a language model call.
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: AfterLLMCallContext)
}

/**
 * Functional interface for handling the initiation of a streaming session for a language learning model (LLM).
 */
public fun interface StartLLMStreamingHandler {
    /**
     * Handles the event when LLM streaming is initiated.
     *
     * @param eventContext The context for the event
     */
    public suspend fun handle(eventContext: StartLLMStreamingContext)
}

/**
 * A functional interface representing a handler that is invoked before executing an operation that involves multiple choices.
 */
public fun interface BeforeExecuteMultipleChoicesHandler {
    /**
     * Handles the logic to be executed before an operation involving multiple choices is executed.
     *
     * @param eventContext The context representing details about the operation, including session information,
     *                     the associated prompt, the language model, and available tools.
     */
    public suspend fun handle(eventContext: BeforeExecuteMultipleChoicesContext)
}

/**
 * Functional interface representing a handler that processes events after the execution of
 * multiple-choice operations in interactions involving a language model.
 */
public fun interface AfterExecuteMultipleChoicesHandler {
    /**
     * Handles the event after executing multiple-choice operations in an interaction involving a language model.
     *
     * @param eventContext The context containing information about the executed operation,
     *                     including session details, prompt, model, tools, and the model's response.
     */
    public suspend fun handle(eventContext: AfterExecuteMultipleChoicesContext)
}
