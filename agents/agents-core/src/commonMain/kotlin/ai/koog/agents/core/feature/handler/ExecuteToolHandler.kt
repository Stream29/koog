package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult

/**
 * Handler for executing tools with customizable behaviors for tool calls,
 * validation errors, failures, and results.
 *
 * This class provides properties that allow defining specific behavior
 * during different stages of a tool's execution process.
 */
public class ExecuteToolHandler {
    /**
     * A variable of type [ToolCallHandler] used to handle tool call operations.
     * It provides a mechanism for executing specific logic when a tool is called
     * with the given arguments.
     *
     * The handler is expected to process a given tool along with its arguments in
     * a suspended context, allowing asynchronous operations during execution.
     */
    public var toolCallHandler: ToolCallHandler =
        ToolCallHandler { sessionId: String, tool: Tool<*, *>, toolArgs: ToolArgs -> }

    /**
     * Defines the handler responsible for processing validation errors that occur when a tool's arguments are invalid.
     * It allows customization of how validation errors are handled during the execution of a tool.
     *
     * This handler is a suspendable function accepting three parameters:
     * - The tool instance where the validation error occurred.
     * - The invalid arguments associated with the tool.
     * - An error message describing the validation issue.
     *
     * The handler can be customized to implement any necessary logic for handling these errors, such as logging,
     * capturing metrics, or halting execution based on the severity of the error.
     */
    public var toolValidationErrorHandler: ToolValidationErrorHandler =
        ToolValidationErrorHandler { _, _, _, _ -> }

    /**
     * A customizable handler invoked when a tool call fails during execution.
     * Defines the behavior for responding to a failure by providing access to
     * the failed tool, its arguments, and the associated exception.
     * This allows for logging, error handling, or recovery strategies to be applied.
     */
    public var toolCallFailureHandler: ToolCallFailureHandler =
        ToolCallFailureHandler { _, _, _ -> }

    /**
     * A variable representing a handler for processing the result of a tool call.
     * The handler is invoked with the tool instance, its arguments, and the result
     * of the tool execution. The result of handling is determined by the specific
     * implementation provided for the ToolCallResultHandler functional interface.
     */
    public var toolCallResultHandler: ToolCallResultHandler =
        ToolCallResultHandler { _, _, _ -> }
}

/**
 * Functional interface representing a handler for tool calls. This interface allows
 * the implementation of custom logic to handle the execution of tools and their associated arguments.
 */
public fun interface ToolCallHandler {
    /**
     * Handles the execution of a given tool using the provided arguments.
     *
     * This function accepts a tool instance and its corresponding arguments, allowing it
     * to perform the tool's specific operation asynchronously.
     *
     * @param tool The tool to be executed. This parameter represents an instance of `Tool`
     *             and includes the logic for its execution and metadata about its behavior.
     * @param toolArgs The arguments required for executing the tool. These arguments are
     *                 used to configure or supply information needed for the tool's operation.
     */
    public suspend fun handle(sessionId: String, tool: Tool<*, *>, toolArgs: ToolArgs)
}

/**
 * A functional interface to handle validation errors that occur during the execution or configuration of a tool.
 * This interface provides a mechanism for processing or logging errors associated with tools and their arguments.
 */
public fun interface ToolValidationErrorHandler {
    /**
     * Handles the tool validation error with the provided tool, arguments, and error message.
     *
     * @param tool The tool instance associated with the validation error.
     * @param toolArgs The arguments passed to the tool when the error occurred.
     * @param error The error message describing the validation issue.
     */
    public suspend fun handle(sessionId: String, tool: Tool<*, *>, toolArgs: ToolArgs, error: String)
}

/**
 * Functional interface for handling failures that occur during the execution of a tool.
 * This interface provides a mechanism to manage errors resulting from tool operations,
 * including access to the tool, its arguments, and the associated error.
 */
public fun interface ToolCallFailureHandler {
    /**
     * Handles a failure that occurs during the execution of a tool call.
     *
     * @param tool The tool that was being executed when the failure occurred.
     * @param toolArgs The arguments that were passed to the tool during execution.
     * @param throwable The exception or error that caused the failure.
     */
    public suspend fun handle(tool: Tool<*, *>, toolArgs: ToolArgs, throwable: Throwable)
}

/**
 * A functional interface designed for handling the result of tool executions in a structured manner.
 * Implementations can define custom logic for processing the result of a tool based on the provided
 * tool instance, its arguments, and the execution result.
 */
public fun interface ToolCallResultHandler {
    /**
     * Handles the execution of a specific tool by processing its arguments and optionally handling its result.
     *
     * @param tool The tool being executed, which defines the operation to be performed.
     * @param toolArgs The arguments required by the tool for execution.
     * @param result An optional result produced by the tool after execution, can be null if not applicable.
     */
    public suspend fun handle(tool: Tool<*, *>, toolArgs: ToolArgs, result: ToolResult?)
}
