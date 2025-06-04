package ai.koog.agents.mcp.client

import ai.koog.agents.features.common.remote.client.engineFactoryProvider
import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerRemoteConfig
import ai.koog.agents.mcp.provider.McpTransportProvider
import io.ktor.client.*
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport

/**
 * A client implementation for interacting with a remote MCP (Message Channel Protocol) server.
 *
 * This class establishes and manages communication with an MCP server using a remote configuration.
 * It provides methods to connect to the server, retrieve available tools, and manage the client lifecycle.
 *
 * @property config Configuration details for the target MCP server, including host, port, and protocol.
 * @constructor Initializes a `RemoteMcpClient` instance with the specified configuration and an optional
 *              HTTP client instance. By default, it uses the `engineFactoryProvider` to define the HTTP client engine.
 */
public class RemoteMcpClient(
    private val config: McpServerRemoteConfig,
) : McpClient {

    override val name: String
        get() = config.name

    private var _client: Client? = null

    override val client: Client
        get() = _client ?: throw IllegalStateException("MCP client is not connected")

    override suspend fun connect() {
        val implementation = Implementation("", "")
        val transport = McpTransportProvider.defaultSseTransport(
            url = "${config.protocol}://${config.host}:${config.port}"
        )

        val mcpClient = Client(clientInfo = implementation)
        mcpClient.connect(transport).also { _client = mcpClient }
    }

    override suspend fun getTools(): List<McpTool> {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }
}
