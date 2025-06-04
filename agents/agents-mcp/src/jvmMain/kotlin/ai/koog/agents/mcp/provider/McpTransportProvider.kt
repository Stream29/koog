package ai.koog.agents.mcp.provider

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Provides factory methods for creating default transport mechanisms used for communication
 * with Message Channel Protocol (MCP) servers.
 *
 * This object includes utilities for creating standard input/output (STDIO) and
 * server-sent events (SSE) transports, which facilitate interactions between
 * clients and servers.
 */
public object McpTransportProvider {

    /**
     * Creates a default standard input/output transport for a provided process.
     *
     * @param process The process whose input and output streams will be used for communication.
     * @return A `StdioClientTransport` configured to communicate with the process using its standard input and output.
     */
    public fun defaultStdioTransport(process: Process): StdioClientTransport {
        return StdioClientTransport(
            input = process.inputStream.asSource().buffered(),
            output = process.outputStream.asSink().buffered()
        )
    }

    /**
     * Creates a default server-sent events (SSE) transport from a provided URL.
     *
     * @param url The URL to be used for establishing an SSE connection.
     * @return An instance of SseClientTransport configured with the given URL.
     */
    public fun defaultSseTransport(url: String): SseClientTransport {
        return SseClientTransport(
            client = HttpClient {
                install(SSE)
            },
            urlString = url,
        )
    }

}