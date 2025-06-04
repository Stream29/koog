package ai.koog.agents.mcp.config

/**
 * Represents the types of communication mechanisms supported by an MCP (Message Channel Protocol) server.
 *
 * This enum is used to distinguish between different kinds of server implementations.
 * Each type determines how the server exchanges data with clients or processes.
 *
 * @property id The string identifier associated with the server type.
 */
public enum class McpServerTransportType(public val id: String) {
    SSE("sse"),
    STDIO("stdio"),
}