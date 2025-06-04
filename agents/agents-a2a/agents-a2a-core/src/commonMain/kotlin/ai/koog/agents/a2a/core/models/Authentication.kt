package ai.koog.agents.a2a.core.models

import kotlinx.serialization.Serializable

/**
 * Authentication context for A2A communications.
 * Authentication is performed out-of-band at the HTTP transport layer.
 */
@Serializable
data class AuthenticationContext(
    val scheme: AuthenticationScheme,
    val credentials: Map<String, String>,
    val roles: List<String> = emptyList(),
    val scopes: List<String> = emptyList()
)

/**
 * Supported authentication schemes as defined by the A2A protocol.
 */
@Serializable
enum class AuthenticationScheme {
    API_KEY,
    BEARER_TOKEN,
    OAUTH2,
    OPENID_CONNECT,
    CUSTOM
}

/**
 * Agent identity information.
 */
@Serializable
data class AgentIdentity(
    val id: String,
    val name: String,
    val version: String,
    val publicKey: String? = null,
    val certificateChain: List<String> = emptyList()
)

/**
 * Security scheme definition as per A2A specification.
 */
@Serializable
data class A2ASecurityScheme(
    val type: String,
    val scheme: String? = null, // for http type
    val bearerFormat: String? = null, // for bearer tokens
    val openIdConnectUrl: String? = null, // for OpenID Connect
    val flows: A2AOAuthFlows? = null, // for OAuth 2.0
    val name: String? = null, // for apiKey
    val `in`: String? = null // for apiKey location (header, query, cookie)
)

/**
 * OAuth 2.0 flows configuration.
 */
@Serializable
data class A2AOAuthFlows(
    val authorizationCode: A2AOAuthFlow? = null,
    val implicit: A2AOAuthFlow? = null,
    val password: A2AOAuthFlow? = null,
    val clientCredentials: A2AOAuthFlow? = null
)

/**
 * Individual OAuth 2.0 flow configuration.
 */
@Serializable
data class A2AOAuthFlow(
    val authorizationUrl: String? = null,
    val tokenUrl: String? = null,
    val refreshUrl: String? = null,
    val scopes: Map<String, String> = emptyMap()
)

/**
 * Constants for A2A authentication headers and schemes.
 */
object A2AAuthConstants {
    const val API_KEY_HEADER = "X-API-Key"
    const val BEARER_PREFIX = "Bearer "
    const val BASIC_PREFIX = "Basic "
    const val AUTH_HEADER = "Authorization"
    const val AGENT_ID_HEADER = "X-Agent-ID"
}

/**
 * A2A SecurityScheme types as per specification.
 */
object A2ASecuritySchemeTypes {
    const val OPENID_CONNECT = "openIdConnect"
    const val OAUTH2 = "oauth2"
    const val API_KEY = "apiKey"
    const val HTTP_BEARER = "http"
    const val HTTP_BASIC = "http"
}