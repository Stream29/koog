package ai.koog.agents.mcp.config

import io.ktor.http.URLProtocol

/**
 * Represents the configuration for an MCP (Message Channel Protocol) server that communicates remotely
 * over a network using a specific host, port, and protocol.
 *
 * This class specializes the `McpServerConfig` interface for remote server configurations
 * and specifies that the communication mechanism used defaults to the `SSE` (Server-Sent Events) type.
 *
 * @property host The host address of the remote server.
 * @property port The port through which the server communicates.
 * @property protocol The communication protocol used by the server, such as HTTP or HTTPS.
 */
public data class McpServerRemoteConfig(
    override val name: String,
    val host: String,
    val port: Int,
    val protocol: URLProtocol,
) : McpServerConfig {

    internal companion object {
        internal val defaultProtocol = URLProtocol.HTTPS
    }
}
