package com.faitapp.llm

/**
 * Configuration for the Dual-LLM Orchestration System
 * 
 * Manages both Persona LLM (smaller, creative) and Factual LLM (larger, accurate)
 * with offline GGUF fallback and online cloud options.
 */
data class DualLLMConfig(
    // Operational Mode
    val mode: LLMMode = LLMMode.HYBRID,
    
    // Offline GGUF Models (using llama.cpp)
    val personaModelPath: String? = null,  // Path to persona GGUF model
    val factualModelPath: String? = null,  // Path to factual GGUF model
    
    // Online Cloud Models (when network available)
    val onlinePersonaProvider: CloudProvider = CloudProvider.OPENAI,
    val onlinePersonaModel: String = "gpt-4o-mini",
    val onlineFactualProvider: CloudProvider = CloudProvider.OPENAI,
    val onlineFactualModel: String = "gpt-4o",
    
    // API Keys
    val openaiApiKey: String? = null,
    val anthropicApiKey: String? = null,
    val geminiApiKey: String? = null,
    
    // Persona Configuration
    val personaName: String = "Fait",
    val personaDescription: String = "A highly intelligent and devoted personal assistant",
    val personaTraits: List<String> = listOf(
        "creative", "engaging", "friendly", "helpful", "precise"
    ),
    
    // Performance Settings
    val maxTokensPersona: Int = 512,
    val maxTokensFactual: Int = 2048,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    
    // Workflow Control
    val enableFactChecking: Boolean = true,
    val enableExpansion: Boolean = true,
    val enableFinalVerification: Boolean = true,
    
    // Abliteration (critical for uncensored responses)
    val requireAbliteratedModels: Boolean = true
)

enum class LLMMode {
    OFFLINE_ONLY,   // Only use local GGUF models
    ONLINE_ONLY,    // Only use cloud APIs
    HYBRID          // Prefer offline, fallback to online
}

enum class CloudProvider {
    OPENAI,
    ANTHROPIC,
    GEMINI,
    EMERGENT_UNIVERSAL  // Uses Emergent LLM key
}

/**
 * Represents each step in the dual-LLM workflow
 */
enum class WorkflowStep {
    P1_INITIAL_CREATIVE,      // Persona: Initial creative response
    F1_ANALYSIS_AUGMENTATION, // Factual: Fact-check + new info
    P2_EXPANSION_ELABORATION, // Persona: Expand with facts
    F2_FINAL_VERIFICATION,    // Factual: Final fact check
    P3_FINAL_PERSONIFICATION  // Persona: Polish persona style
}

/**
 * Result from each workflow step
 */
data class StepResult(
    val step: WorkflowStep,
    val output: String,
    val modelUsed: String,
    val isOffline: Boolean,
    val latencyMs: Long,
    val tokensUsed: Int? = null
)

/**
 * Complete orchestration result
 */
data class OrchestrationResult(
    val finalResponse: String,
    val steps: List<StepResult>,
    val totalLatencyMs: Long,
    val offlineSteps: Int,
    val onlineSteps: Int,
    val success: Boolean,
    val error: String? = null
)
