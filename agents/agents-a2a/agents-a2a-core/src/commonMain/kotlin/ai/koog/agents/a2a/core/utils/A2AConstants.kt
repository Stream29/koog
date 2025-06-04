package ai.koog.agents.a2a.core.utils

/**
 * Constants used throughout the A2A protocol implementation.
 */
object A2AConstants {
    /**
     * JSON-RPC version string.
     */
    const val JSONRPC_VERSION = "2.0"
    
    /**
     * Standard A2A endpoint path.
     */
    const val A2A_ENDPOINT_PATH = "/a2a"
    
    /**
     * Standard A2A streaming endpoint path.
     */
    const val A2A_STREAM_ENDPOINT_PATH = "/a2a/stream"
    
    /**
     * Well-known agent card endpoint.
     */
    const val AGENT_CARD_ENDPOINT = "/.well-known/agent.json"
    
    /**
     * Content type for JSON-RPC requests.
     */
    const val JSONRPC_CONTENT_TYPE = "application/json"
    
    /**
     * Content type for Server-Sent Events.
     */
    const val SSE_CONTENT_TYPE = "text/event-stream"
    
    /**
     * Default timeout for A2A requests (30 seconds).
     */
    const val DEFAULT_TIMEOUT_MS = 30000L
    
    /**
     * Default heartbeat interval for streaming (30 seconds).
     */
    const val DEFAULT_HEARTBEAT_INTERVAL_MS = 30000L
    
    /**
     * Default buffer size for streaming responses.
     */
    const val DEFAULT_STREAM_BUFFER_SIZE = 1024
}