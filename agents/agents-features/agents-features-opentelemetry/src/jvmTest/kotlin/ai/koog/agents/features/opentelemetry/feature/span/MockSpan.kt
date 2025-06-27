package ai.koog.agents.features.opentelemetry.feature.span

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import java.util.concurrent.TimeUnit

/**
 * A mock implementation of TraceSpanBase for testing.
 * This class tracks whether the span has been started and ended.
 */
internal class MockSpan(
    tracer: Tracer,
    parentSpan: TraceSpanBase? = null,
    override val spanId: String
) : TraceSpanBase(tracer, parentSpan) {
    
    var isStarted = false
    var isEnded = false
    var currentStatus: StatusCode? = null
    var currentStatusDescription: String? = null
    val attributes = mutableMapOf<String, Any?>()
    val events = mutableListOf<String>()
    
    fun start() {
        isStarted = true
        // Create a mock Span that will track when it's ended
        val mockSpan = object : Span {
            override fun <T : Any?> setAttribute(key: AttributeKey<T?>, value: T?): Span = this
            
            // These are the correct overrides for the Span interface
            override fun setAttribute(key: String?, value: String?): Span = this
            override fun setAttribute(key: String?, value: Boolean): Span = this
            override fun setAttribute(key: String?, value: Long): Span = this
            override fun setAttribute(key: String?, value: Double): Span = this
            override fun setAttribute(key: AttributeKey<Long>?, value: Int): Span = this
            
            override fun addEvent(name: String, attributes: Attributes): Span = this
            override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span = this
            
            override fun setStatus(statusCode: StatusCode, description: String): Span {
                currentStatus = statusCode
                currentStatusDescription = description
                return this
            }
            
            override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span = this
            override fun updateName(name: String): Span = this
            
            override fun end() { 
                isEnded = true 
            }
            
            override fun end(timestamp: Long, unit: TimeUnit) { 
                isEnded = true 
            }
            
            override fun getSpanContext(): SpanContext = throw UnsupportedOperationException("Not implemented in test")
            override fun isRecording(): Boolean = isStarted && !isEnded
            override fun storeInContext(context: Context): Context = context
        }
        
        // Use reflection to set the _span field in TraceSpanBase
        val spanField = TraceSpanBase::class.java.getDeclaredField("_span")
        spanField.isAccessible = true
        spanField.set(this, mockSpan)
        
        // Use reflection to set the _context field in TraceSpanBase
        val contextField = TraceSpanBase::class.java.getDeclaredField("_context")
        contextField.isAccessible = true
        contextField.set(this, Context.current())
    }
}