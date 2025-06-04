package ai.koog.agents.mcp.config

/**
 * Represents the configuration for an MCP (Message Channel Protocol) server
 * that communicates using standard input/output (STDIO).
 *
 * This class implements the `McpServerConfig` interface and specifies the details
 * required to launch a server based on executing a command with arguments and
 * environment variables.
 *
 * @property command The primary command to execute for initializing the server.
 * @property args A list of arguments passed to the command during execution.
 * @property env A map of environment variables to set for the command execution.
 */
public data class McpServerCommandConfig(
    override val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
) : McpServerConfig
