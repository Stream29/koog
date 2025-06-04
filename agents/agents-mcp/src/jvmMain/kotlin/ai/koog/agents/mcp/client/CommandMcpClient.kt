package ai.koog.agents.mcp.client

import ai.koog.agents.mcp.McpTool
import ai.koog.agents.mcp.config.McpServerCommandConfig
import ai.koog.agents.mcp.parser.DefaultMcpToolDescriptorParser
import ai.koog.agents.mcp.parser.McpToolDescriptorParser
import ai.koog.agents.mcp.parser.ToolConverter.toTools
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import kotlinx.coroutines.delay
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * A client implementation of `McpClient` designed for interacting with an MCP server
 * through a command-based process.
 *
 * This client establishes a connection to the server,
 * communicates via standard input/output, and manages the client lifecycle.
 *
 * @param config The configuration for connecting to the MCP server, including the command,
 *               arguments, and environment variables.
 * @param version Optional version of the client. Defaults to `DEFAULT_MCP_CLIENT_VERSION`.
 */
public class CommandMcpClient(
    public val config: McpServerCommandConfig,
    private val mcpToolParser: McpToolDescriptorParser = DefaultMcpToolDescriptorParser,
    private val version: String = DEFAULT_MCP_CLIENT_VERSION,
) : McpClient {

    /**
     * Defines constants and utility properties related to the behavior and the configuration of the MCP client.
     */
    public companion object {

        private val logger = KotlinLogging.logger("ai.koog.agents.mcp.client.CommandBaseMcpClient")

        /**
         * Default name for the MCP client when connecting to an MCP server.
         */
        public const val DEFAULT_MCP_CLIENT_NAME: String = "mcp-client-cli"

        /**
         * Default version for the MCP client when connecting to an MCP server.
         */
        public const val DEFAULT_MCP_CLIENT_VERSION: String = "1.0.0"
    }

    override val name: String
        get() = config.name

    private var _client: Client? = null

    override val client: Client
        get() = _client ?: throw IllegalStateException("MCP client is not connected")

    private var _process: Process? = null

    public fun createTransport(transportConfig: TransportConfig): Transport {

    }

    override suspend fun connect() {
        // 1. Start a process
        val process = startProcess().also { _process = it }

        // 2. Create a transport (based on mcp)
        val transport = defaultStdioTransport(process)

        // 3. Connect to a client to get tools
        val mcpClient = Client(clientInfo = Implementation(name = name, version = version))

        mcpClient.connect(transport).also { _client = mcpClient }
    }

    override suspend fun getTools(): List<McpTool> {
        val listToolsResult = client.listTools()
        return listToolsResult?.toTools(client = client, parser = mcpToolParser) ?: emptyList()
    }

    override suspend fun close() {
        if (_client != null) {
            _client?.close()
        }

        if (_process != null) {
            _process?.destroy()
        }
    }

    //region Private Methods

    private suspend fun startProcess(): Process {
        val processBuilder = ProcessBuilder(config.command)

        if (config.args.isNotEmpty()) {
            processBuilder.command().addAll(config.args)
        }

        if (config.env.isNotEmpty()) {
            processBuilder.environment().putAll(config.env)
        }

        val process = processBuilder.start()

        // TODO: Replace with a proper check for a process to start and make necessary preparations.
        delay(2000)

        return process
    }

    private suspend fun tryStartProcess(): Process? {
        try {
            return startProcess()
        }
        catch (t: Throwable) {
            logger.error(t) { "Failed to start MCP server process" }
            return null
        }
    }
}