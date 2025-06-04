package ai.koog.agents.mcp.parser

import ai.koog.agents.core.tools.ToolDescriptor
import io.modelcontextprotocol.kotlin.sdk.Tool

/**
 * Parsers tool definition from MCP SDK to our tool descriptor format.
 */
public interface McpToolDescriptorParser {
    /**
     * Parses an SDK tool representation into a standardized ToolDescriptor format.
     *
     * @param sdkTool The SDKTool instance containing tool information to be parsed.
     * @return The parsed ToolDescriptor, representing the tool in a standardized format.
     */
    public fun parse(sdkTool: Tool): ToolDescriptor
}
