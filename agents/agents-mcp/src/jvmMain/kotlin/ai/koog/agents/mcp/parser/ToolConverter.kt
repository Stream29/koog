package ai.koog.agents.mcp.parser

import ai.koog.agents.mcp.McpTool
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.client.Client

internal object ToolConverter {

    private val logger = KotlinLogging.logger(ToolConverter::class.qualifiedName ?: "ai.koog.agents.mcp.utils.ToolConverter")

    internal fun ListToolsResult.toTools(
        client: Client,
        parser: McpToolDescriptorParser
    ): List<McpTool> =
        tools.mapNotNull { sdkTool ->
            try {
                val toolDescriptor = parser.parse(sdkTool)
                McpTool(client, toolDescriptor)
            }
            catch (e: Throwable) {
                logger.error(e) { "Failed to parse descriptor parameters for tool: '${sdkTool.name}'" }
                null
            }
        }
}
