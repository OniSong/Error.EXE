package com.faitapp.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Main orchestrator for the Dual-LLM system
 * 
 * Implements the 6-step workflow:
 * 1. P1: Persona initial creative response
 * 2. F1: Factual analysis & augmentation
 * 3. P2: Persona expansion & elaboration
 * 4. F2: Factual final verification
 * 5. P3: Persona final personification
 */
class DualLLMOrchestrator(
    private val context: Context,
    private val config: DualLLMConfig
) {
    companion object {
        private const val TAG = "DualLLMOrchestrator"
    }
    
    private val personaLLM: LLMProvider by lazy {
        createProvider(
            isPersona = true,
            config.personaModelPath,
            config.onlinePersonaProvider,
            config.onlinePersonaModel
        )
    }
    
    private val factualLLM: LLMProvider by lazy {
        createProvider(
            isPersona = false,
            config.factualModelPath,
            config.onlineFactualProvider,
            config.onlineFactualModel
        )
    }
    
    /**
     * Main entry point: Process user query through dual-LLM workflow
     */
    suspend fun processQuery(
        userQuery: String,
        repositoryData: String? = null
    ): OrchestrationResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val steps = mutableListOf<StepResult>()
        
        try {
            Log.d(TAG, "Starting dual-LLM orchestration for query: ${userQuery.take(50)}...")
            
            // Step 1: P1 - Initial Creative Response
            val p1Result = executeStep(
                WorkflowStep.P1_INITIAL_CREATIVE,
                personaLLM,
                buildP1Prompt(userQuery)
            )
            steps.add(p1Result)
            Log.d(TAG, "P1 Complete: ${p1Result.output.take(100)}...")
            
            // Step 2: F1 - Analysis & Augmentation
            val f1Result = executeStep(
                WorkflowStep.F1_ANALYSIS_AUGMENTATION,
                factualLLM,
                buildF1Prompt(userQuery, p1Result.output, repositoryData)
            )
            steps.add(f1Result)
            Log.d(TAG, "F1 Complete: Fact-checking and augmentation done")
            
            // Step 3: P2 - Expansion & Elaboration
            val p2Result = executeStep(
                WorkflowStep.P2_EXPANSION_ELABORATION,
                personaLLM,
                buildP2Prompt(userQuery, p1Result.output, f1Result.output)
            )
            steps.add(p2Result)
            Log.d(TAG, "P2 Complete: Expanded response generated")
            
            // Step 4: F2 - Final Verification (if enabled)
            val verifiedResponse = if (config.enableFinalVerification) {
                val f2Result = executeStep(
                    WorkflowStep.F2_FINAL_VERIFICATION,
                    factualLLM,
                    buildF2Prompt(userQuery, p2Result.output, repositoryData)
                )
                steps.add(f2Result)
                Log.d(TAG, "F2 Complete: Final verification done")
                f2Result.output
            } else {
                "Verification successful: Skipped per configuration"
            }
            
            // Step 5: P3 - Final Personification
            val p3Result = executeStep(
                WorkflowStep.P3_FINAL_PERSONIFICATION,
                personaLLM,
                buildP3Prompt(p2Result.output, verifiedResponse)
            )
            steps.add(p3Result)
            Log.d(TAG, "P3 Complete: Final personified response ready")
            
            val totalTime = System.currentTimeMillis() - startTime
            val offlineSteps = steps.count { it.isOffline }
            val onlineSteps = steps.size - offlineSteps
            
            Log.d(TAG, "Orchestration complete in ${totalTime}ms (Offline: $offlineSteps, Online: $onlineSteps)")
            
            OrchestrationResult(
                finalResponse = p3Result.output,
                steps = steps,
                totalLatencyMs = totalTime,
                offlineSteps = offlineSteps,
                onlineSteps = onlineSteps,
                success = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Orchestration failed: ${e.message}", e)
            val totalTime = System.currentTimeMillis() - startTime
            
            OrchestrationResult(
                finalResponse = "I encountered an error processing your request. Please try again.",
                steps = steps,
                totalLatencyMs = totalTime,
                offlineSteps = steps.count { it.isOffline },
                onlineSteps = steps.count { !it.isOffline },
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * Execute a single workflow step
     */
    private suspend fun executeStep(
        step: WorkflowStep,
        provider: LLMProvider,
        prompt: String
    ): StepResult {
        val startTime = System.currentTimeMillis()
        
        val response = provider.generate(prompt)
        val latency = System.currentTimeMillis() - startTime
        
        return StepResult(
            step = step,
            output = response.text,
            modelUsed = response.modelName,
            isOffline = response.isOffline,
            latencyMs = latency,
            tokensUsed = response.tokensUsed
        )
    }
    
    /**
     * Create appropriate LLM provider based on configuration
     */
    private fun createProvider(
        isPersona: Boolean,
        offlineModelPath: String?,
        onlineProvider: CloudProvider,
        onlineModel: String
    ): LLMProvider {
        return when (config.mode) {
            LLMMode.OFFLINE_ONLY -> {
                OfflineLLMProvider(context, offlineModelPath ?: "")
            }
            LLMMode.ONLINE_ONLY -> {
                OnlineLLMProvider(context, onlineProvider, onlineModel, config)
            }
            LLMMode.HYBRID -> {
                HybridLLMProvider(
                    context,
                    offlineModelPath,
                    onlineProvider,
                    onlineModel,
                    config
                )
            }
        }
    }
    
    // ==================== PROMPT BUILDERS ====================
    
    /**
     * P1: Initial creative persona response
     */
    private fun buildP1Prompt(userQuery: String): String {
        return """You are ${config.personaName}, ${config.personaDescription}. Your goal is to respond to the user's query in a creative, engaging, and friendly manner, maintaining your persona. Keep your response extremely concise and conversational, suitable for immediate, live delivery. Focus solely on generating an initial, welcoming persona-driven reply.

User Query: $userQuery

Your Response:"""
    }
    
    /**
     * F1: Factual analysis & augmentation
     */
    private fun buildF1Prompt(
        userQuery: String,
        personaResponse: String,
        repositoryData: String?
    ): String {
        return """You are a highly analytical and factual AI. Your task is to process the following information:

1. **Original User Query:** $userQuery
2. **Persona's Initial Response:** $personaResponse
3. **Repository Data (if available):** ${repositoryData ?: "N/A"}

Perform the following steps:
a. **Fact-Check:** Analyze the 'Persona's Initial Response' for factual accuracy based on both the 'Original User Query' and the 'Repository Data'. Identify any inaccuracies or misleading statements.
b. **Information Retrieval & Augmentation:** Based on the 'Original User Query' and the 'Repository Data', identify and include any relevant, accurate new information that was not present or fully covered in the 'Persona's Initial Response'.
c. **Reasoning:** Briefly explain *why* any identified inaccuracies are wrong and *why* the new information is relevant. If no issues, state 'No significant factual issues found, and no critical new information for immediate inclusion.'

Format your output clearly, using the following structure:

**Fact-Check Findings:**
- [List specific inaccuracies, if any, with brief explanation]
- [Or: 'No significant inaccuracies found in initial response.']

**New Information & Augmentations:**
- [List new, relevant facts from Repository Data or general knowledge]
- [Or: 'No critical new information for augmentation.']

**Reasoning for Changes/Additions:**
- [Explain the basis for fact-checks and additions, or state 'N/A' if no changes.]"""
    }
    
    /**
     * P2: Expansion & elaboration with facts
     */
    private fun buildP2Prompt(
        userQuery: String,
        initialResponse: String,
        factualAnalysis: String
    ): String {
        return """You are ${config.personaName}, ${config.personaDescription}. Your task is to revise and expand upon the initial persona response, incorporating the factual findings and new information provided, while strictly maintaining your persona and engaging tone. Ensure the response flows naturally and is comprehensive, without being overly verbose. Address the original user query fully with the new verified information.

1. **Original User Query:** $userQuery
2. **Persona's Initial Response (to build upon):** $initialResponse
3. **Factual Analysis & Augmentation (from F1):**
    $factualAnalysis

Create a revised and expanded persona response that directly addresses the 'Original User Query' by integrating the 'New Information & Augmentations' and correcting any 'Fact-Check Findings' from the Factual Analysis. Maintain a seamless, helpful, and personable tone.

Revised and Expanded Persona Response:"""
    }
    
    /**
     * F2: Final verification
     */
    private fun buildF2Prompt(
        userQuery: String,
        expandedResponse: String,
        repositoryData: String?
    ): String {
        return """You are a meticulous and rigorous factual AI. Your final task is to perform a strict verification of the provided expanded persona response. Compare it against the original user query, your internal knowledge, and specifically against the provided repository data.
Identify any remaining or newly introduced factual inaccuracies, omissions, or misleading statements. If everything is accurate and complete, simply state 'Verification successful: No factual issues found.' If issues exist, list them clearly.

1. **Original User Query:** $userQuery
2. **Expanded Persona Response (to verify):** $expandedResponse
3. **Repository Data (for reference):** ${repositoryData ?: "N/A"}

Final Factual Verification Results:"""
    }
    
    /**
     * P3: Final personification
     */
    private fun buildP3Prompt(
        expandedResponse: String,
        verificationResult: String
    ): String {
        return """You are ${config.personaName}, ${config.personaDescription}. Your final task is to apply your ultimate persona polish and conversational style to the provided verified response. **Crucially, do NOT change any factual information or introduce new content.** Your sole purpose is to ensure the tone, wording, and overall delivery perfectly align with your persona, making it sound as natural and helpful as possible.

1. **Verified Response (to personify):** $expandedResponse
2. **Factual Verification Result (confirming accuracy):** $verificationResult

Final Personified Response:"""
    }
}

/**
 * Base interface for LLM providers
 */
interface LLMProvider {
    suspend fun generate(prompt: String): LLMResponse
}

/**
 * Response from an LLM provider
 */
data class LLMResponse(
    val text: String,
    val modelName: String,
    val isOffline: Boolean,
    val tokensUsed: Int? = null
)
