@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.a2a.core.jsonrpc.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Default JSON-RPC version.
 */
public const val JSONRPC_VERSION: String = "2.0"

/**
 * A uniquely identifying ID for a request in JSON-RPC.
 */
@Serializable(with = RequestIdSerializer::class)
public sealed interface RequestId {
    @Serializable
    public data class StringId(val value: String) : RequestId

    @Serializable
    public data class NumberId(val value: Long) : RequestId
}

@Serializable
public data class JSONRPCError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable(with = JSONRPCMessageSerializer::class)
public sealed interface JSONRPCMessage {
    public val jsonrpc: String
}

@Serializable(with = JSONRPCResponseSerializer::class)
public sealed interface JSONRPCResponse : JSONRPCMessage

@Serializable
public data class JSONRPCRequest(
    public val id: RequestId,
    val method: String,
    val params: JsonElement?,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

@Serializable
public data class JSONRPCNotification(
    val method: String,
    val params: JsonElement?,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCMessage

@Serializable
public data class JSONRPCSuccessResponse(
    public val id: RequestId,
    public val result: JsonElement,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCResponse

@Serializable
public data class JSONRPCErrorResponse(
    public val id: RequestId?,
    public val error: JSONRPCError,
    @EncodeDefault
    override val jsonrpc: String = JSONRPC_VERSION,
) : JSONRPCResponse
