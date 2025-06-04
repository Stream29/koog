package ai.koog.agents.mcp.provider

import ai.koog.agents.mcp.client.CommandMcpClient
import ai.koog.agents.mcp.client.McpClient
import ai.koog.agents.mcp.client.RemoteMcpClient
import ai.koog.agents.mcp.config.McpServerCommandConfig
import ai.koog.agents.mcp.config.McpServerConfig
import ai.koog.agents.mcp.config.McpServerRemoteConfig
import io.github.oshai.kotlinlogging.KotlinLogging

public object McpClientProvider {

    private val logger = KotlinLogging.logger(McpClientProvider::class.qualifiedName ?: "ai.koog.agents.mcp.provider.McpClientProvider")

    public fun provideClient(config: McpServerConfig): McpClient {
        val mcpClient: McpClient =
            when (config) {
                is McpServerRemoteConfig -> {
                    RemoteMcpClient(config)
                }

                is McpServerCommandConfig -> {
                    CommandMcpClient(config)
                }

                else -> {
                    error("Unsupported MCP server config type: ${config::class.simpleName}")
                }
            }

        logger.debug { "Defined MCP client to use: $mcpClient" }
        return mcpClient
    }
}
