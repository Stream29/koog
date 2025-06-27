package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import java.util.concurrent.TimeUnit

class MockSpan : Span {
    override fun <T : Any?> setAttribute(
        key: AttributeKey<T?>,
        value: T?
    ): Span? {
        TODO("Not yet implemented")
    }

    override fun addEvent(
        name: String,
        attributes: Attributes
    ): Span? {
        TODO("Not yet implemented")
    }

    override fun addEvent(
        name: String,
        attributes: Attributes,
        timestamp: Long,
        unit: TimeUnit
    ): Span? {
        TODO("Not yet implemented")
    }

    override fun setStatus(
        statusCode: StatusCode,
        description: String
    ): Span? {
        TODO("Not yet implemented")
    }

    override fun recordException(
        exception: Throwable,
        additionalAttributes: Attributes
    ): Span? {
        TODO("Not yet implemented")
    }

    override fun updateName(name: String): Span? {
        TODO("Not yet implemented")
    }

    override fun end() {
        TODO("Not yet implemented")
    }

    override fun end(timestamp: Long, unit: TimeUnit) {
        TODO("Not yet implemented")
    }

    override fun getSpanContext(): SpanContext? {
        TODO("Not yet implemented")
    }

    override fun isRecording(): Boolean {
        TODO("Not yet implemented")
    }
}