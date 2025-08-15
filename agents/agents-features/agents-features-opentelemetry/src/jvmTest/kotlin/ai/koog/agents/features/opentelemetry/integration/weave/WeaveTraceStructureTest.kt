package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase

/**
 * Test traces OpenTelemetry structure conformance to Langfuse and Weave data models.
 */
class WeaveTraceStructureTest : TraceStructureTestBase({ addWeaveExporter() })
