package com.faitapp.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline LLM Provider using llama.cpp for local GGUF model inference
 * 
 * This provider runs abliterated GGUF models locally on-device using llama.cpp JNI bindings.
 * No internet connection required.
 */
class OfflineLLMProvider(
    private val context: Context,
    private val modelPath: String
) : LLMProvider {
    
    companion object {
        private const val TAG = "OfflineLLMProvider"
        
        // Load llama.cpp native library
        init {
            try {
                System.loadLibrary("llama-android")
                Log.d(TAG, "llama-android native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load llama-android library: ${e.message}")
            }
        }
        
        // Native methods (implemented in C++ via JNI)
        @JvmStatic
        external fun llamaInit(modelPath: String, nThreads: Int): Long
        
        @JvmStatic
        external fun llamaGenerate(
            contextPtr: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            topP: Float
        ): String
        
        @JvmStatic
        external fun llamaFree(contextPtr: Long)
        
        @JvmStatic
        external fun llamaGetModelName(contextPtr: Long): String
    }
    
    private var contextPtr: Long = 0
    private var isInitialized = false
    
    /**
     * Initialize the llama.cpp model
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Log.w(TAG, "Model already initialized")
                return@withContext true
            }
            
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $modelPath")
                return@withContext false
            }
            
            Log.d(TAG, "Initializing llama.cpp with model: $modelPath")
            Log.d(TAG, "Model size: ${modelFile.length() / 1024 / 1024} MB")
            
            // Initialize with number of CPU threads
            val nThreads = Runtime.getRuntime().availableProcessors()
            contextPtr = llamaInit(modelPath, nThreads)
            
            if (contextPtr == 0L) {
                Log.e(TAG, "Failed to initialize llama.cpp context")
                return@withContext false
            }
            
            isInitialized = true
            val modelName = llamaGetModelName(contextPtr)
            Log.d(TAG, "Model initialized successfully: $modelName")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model: ${e.message}", e)
            false
        }
    }
    
    /**
     * Generate text using the local GGUF model
     */
    override suspend fun generate(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                val initSuccess = initialize()
                if (!initSuccess) {
                    throw IllegalStateException("Failed to initialize GGUF model")
                }
            }
            
            Log.d(TAG, "Generating with offline model, prompt length: ${prompt.length}")
            
            val response = llamaGenerate(
                contextPtr = contextPtr,
                prompt = prompt,
                maxTokens = 512,
                temperature = 0.7f,
                topP = 0.9f
            )
            
            Log.d(TAG, "Generated response length: ${response.length}")
            
            LLMResponse(
                text = response,
                modelName = llamaGetModelName(contextPtr),
                isOffline = true,
                tokensUsed = null  // Token counting would need additional implementation
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating text: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        if (isInitialized && contextPtr != 0L) {
            try {
                llamaFree(contextPtr)
                contextPtr = 0
                isInitialized = false
                Log.d(TAG, "Model resources freed")
            } catch (e: Exception) {
                Log.e(TAG, "Error freeing model: ${e.message}")
            }
        }
    }
    
    protected fun finalize() {
        shutdown()
    }
}

/**
 * Utility for managing GGUF model files
 */
object GGUFModelManager {
    private const val TAG = "GGUFModelManager"
    
    /**
     * Get the path to the persona GGUF model
     */
    fun getPersonaModelPath(context: Context): String? {
        // Check in filesDir first (where FileRegistry stores files)
        val fileRegistry = com.faitapp.core.FileRegistry(context)
        val ggufPath = fileRegistry.getGgufPath()
        
        if (ggufPath != null && File(ggufPath).exists()) {
            Log.d(TAG, "Found GGUF model at: $ggufPath")
            return ggufPath
        }
        
        // Check common locations
        val locations = listOf(
            "${context.filesDir}/models/persona.gguf",
            "${context.filesDir}/models/llama-persona.gguf",
            "${context.getExternalFilesDir(null)}/models/persona.gguf"
        )
        
        for (path in locations) {
            if (File(path).exists()) {
                Log.d(TAG, "Found GGUF model at: $path")
                return path
            }
        }
        
        Log.w(TAG, "No GGUF model found in standard locations")
        return null
    }
    
    /**
     * Verify if a GGUF file is valid (abliterated model check)
     */
    fun isAbliteratedModel(modelPath: String): Boolean {
        // TODO: Implement abliteration verification
        // This would check model metadata or run test prompts
        // For now, assume all local models are abliterated as required
        return true
    }
    
    /**
     * Get recommended GGUF models for download
     */
    fun getRecommendedModels(): List<GGUFModelInfo> {
        return listOf(
            GGUFModelInfo(
                name = "Llama 3.2 1B Abliterated",
                size = "1.3 GB",
                quantization = "Q4_K_M",
                url = "https://huggingface.co/...",  // Actual download URL
                description = "Fast persona model, good for quick responses"
            ),
            GGUFModelInfo(
                name = "Llama 3.1 8B Abliterated",
                size = "5.2 GB",
                quantization = "Q4_K_M",
                url = "https://huggingface.co/...",
                description = "Balanced factual model, good accuracy"
            ),
            GGUFModelInfo(
                name = "Mixtral 8x7B Abliterated",
                size = "26 GB",
                quantization = "Q4_K_M",
                url = "https://huggingface.co/...",
                description = "High-quality factual model, requires powerful device"
            )
        )
    }
}

data class GGUFModelInfo(
    val name: String,
    val size: String,
    val quantization: String,
    val url: String,
    val description: String
)
