package com.jetbrains.example.kotlin_agents_demo_app.agents.local

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import com.google.ai.edge.localagents.fc.GemmaFormatter
import com.google.ai.edge.localagents.fc.HammerFormatter
import com.google.ai.edge.localagents.fc.LlamaFormatter
import com.google.ai.edge.localagents.fc.ModelFormatter
import com.jetbrains.example.kotlin_agents_demo_app.agents.local.AndroidLocalModels.Chat

fun getFormatterByModelId(modelId: String): ModelFormatter {
    return when (modelId) {
        Chat.Hammer.id -> HammerFormatter()
        Chat.Gemma.id -> GemmaFormatter()
        Chat.Llama.id -> LlamaFormatter()
        else -> throw IllegalArgumentException("Unknown model id: $modelId")
    }
}

object AndroidLocalModels : LLModelDefinitions {
    object Chat {
        /**
         * https://huggingface.co/litert-community/Gemma3-1B-IT
         */
        val Gemma: LLModel = LLModel(
            provider = AndroidLocalLLMProvider,
            id = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048",
            capabilities = listOf(
                LLMCapability.Completion
            )
        )

        /**
         * https://huggingface.co/litert-community/Hammer2.1-1.5b
         */
        val Hammer: LLModel = LLModel(
            provider = AndroidLocalLLMProvider,
            id = "Hammer2.1-1.5b_multi-prefill-seq_q8_ekv4096",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.Completion
            )
        )

        /**
         * https://huggingface.co/litert-community/Llama-3.2-1B-Instruct
         */
        val Llama: LLModel = LLModel(
            provider = AndroidLocalLLMProvider,
            id = "Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280",
            capabilities = listOf(
                LLMCapability.Tools,
                LLMCapability.Completion
            )
        )
    }
}
