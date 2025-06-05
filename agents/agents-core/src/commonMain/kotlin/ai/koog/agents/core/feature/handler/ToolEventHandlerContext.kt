package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult

public interface ToolEventHandlerContext : EventHandlerContext

public data class ToolCallEventHandlerContext(
    val sessionId: String,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs
) : ToolEventHandlerContext

public data class ToolValidationErrorHandlerContext(
    val sessionId: String,
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val error: String
) : ToolEventHandlerContext

public data class ToolCallFailureHandlerContext(
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val throwable: Throwable
) : ToolEventHandlerContext

public data class ToolCallResultHandler(
    val tool: Tool<*, *>,
    val toolArgs: ToolArgs,
    val result: ToolResult?
) : ToolEventHandlerContext
