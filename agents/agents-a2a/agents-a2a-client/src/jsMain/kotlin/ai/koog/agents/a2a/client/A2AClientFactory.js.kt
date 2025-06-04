package ai.koog.agents.a2a.client

import ai.koog.agents.a2a.core.A2AClient
import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * JS-specific factory extensions for A2A clients.
 */
actual object PlatformA2AClientFactory {
    
    /**
     * Create a default A2A client optimized for JavaScript/Browser with JS engine.
     */
    actual fun createPlatformDefault(
        config: A2AClientConfig,
        enableLogging: Boolean
    ): A2AClient {
        val httpClient = HttpClient(Js) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }
        }
        
        return KtorA2AClient(httpClient, config)
    }
}