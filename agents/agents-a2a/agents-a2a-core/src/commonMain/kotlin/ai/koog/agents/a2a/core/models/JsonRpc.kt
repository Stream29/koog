package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 request message as defined by the A2A protocol.
 */
@Serializable
data class JsonRpcRequest(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
    val id: String
)

/**
 * JSON-RPC 2.0 response message as defined by the A2A protocol.
 */
@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: String
)

/**
 * JSON-RPC 2.0 error object.
 */
@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Standard JSON-RPC 2.0 error codes.
 */
object JsonRpcErrorCodes {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
}

/**
 * A2A-specific error codes as defined by the protocol.
 */
object A2AErrorCodes {
    const val AGENT_UNAVAILABLE = -40001
    const val TASK_TIMEOUT = -40002
    const val CAPABILITY_MISMATCH = -40003
    const val AUTHENTICATION_FAILED = -40004
    const val RESOURCE_EXHAUSTED = -40005
}