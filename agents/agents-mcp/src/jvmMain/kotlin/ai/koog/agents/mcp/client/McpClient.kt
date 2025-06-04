package ai.koog.agents.mcp.client

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.utils.Closeable
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.shared.Transport

/**
 * Represents a client for interacting with an MCP server.
 *
 * This interface provides the necessary methods to establish a connection with the server,
 * retrieve available tools, and manage the lifecycle of the client.
 *
 * The client class extends the `Closeable`
 * interface to ensure that resources are properly released when the client is no longer needed.
 */
public interface McpClient : Closeable {

    /**
     * The name of the MCP client, used to identify the client instance when interacting with an MCP server.
     */
    public val name: String

    /**
     * Provides access to an instance of the underlying MCP client responsible for
     * enabling communication and interaction with the MCP server.
     */
    public val client: Client

    /**
     * Establishes a connection to the MCP server.
     *
     * This method is responsible for initiating the connection process with the server,
     * which may involve setting up transports, initializing resources, or preparing a
     * client instance for communication.
     */
    public suspend fun connect()

    /**
     * Retrieves a list of available tools from the MCP server.
     *
     * @return A list of McpTool instances representing the tools available on the MCP server.
     */
    public suspend fun getTools(): List<McpTool>
}
