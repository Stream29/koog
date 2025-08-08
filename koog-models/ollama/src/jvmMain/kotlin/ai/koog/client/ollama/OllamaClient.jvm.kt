package ai.koog.client.ollama

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO

internal actual fun engineFactoryProvider(): HttpClientEngineFactory<*> = CIO
