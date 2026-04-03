package com.faitapp.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Online LLM Provider for cloud-based models
 * 
 * Supports OpenAI, Anthropic Claude, and Google Gemini
 * Uses abliterated models when available
 */
class OnlineLLMProvider(
    private val context: Context,
    private val provider: CloudProvider,
    private val model: String,
    private val config: DualLLMConfig
) : LLMProvider {
    
    companion object {
        private const val TAG = "OnlineLLMProvider"
        private const val TIMEOUT_SECONDS = 60L
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    
    override suspend fun generate(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                CloudProvider.OPENAI -> generateOpenAI(prompt)
                CloudProvider.ANTHROPIC -> generateAnthropic(prompt)
                CloudProvider.GEMINI -> generateGemini(prompt)
                CloudProvider.EMERGENT_UNIVERSAL -> generateWithEmergentKey(prompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating with $provider: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Generate using OpenAI API
     */
    private fun generateOpenAI(prompt: String): LLMResponse {
        val apiKey = config.openaiApiKey 
            ?: throw IllegalStateException("OpenAI API key not configured")
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokensFactual)
            put("top_p", config.topP)
        }.toString()
        
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("OpenAI API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from OpenAI")
            
            val json = JSONObject(responseBody)
            val text = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            
            val tokensUsed = json.getJSONObject("usage")
                .getInt("total_tokens")
            
            Log.d(TAG, "OpenAI response received, tokens: $tokensUsed")
            
            return LLMResponse(
                text = text,
                modelName = model,
                isOffline = false,
                tokensUsed = tokensUsed
            )
        }
    }
    
    /**
     * Generate using Anthropic Claude API
     */
    private fun generateAnthropic(prompt: String): LLMResponse {
        val apiKey = config.anthropicApiKey 
            ?: throw IllegalStateException("Anthropic API key not configured")
        
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", config.maxTokensFactual)
            put("temperature", config.temperature)
            put("top_p", config.topP)
        }.toString()
        
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Anthropic API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from Anthropic")
            
            val json = JSONObject(responseBody)
            val text = json.getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
            
            val tokensUsed = json.getJSONObject("usage")
                .getInt("input_tokens") + 
                json.getJSONObject("usage").getInt("output_tokens")
            
            Log.d(TAG, "Anthropic response received, tokens: $tokensUsed")
            
            return LLMResponse(
                text = text,
                modelName = model,
                isOffline = false,
                tokensUsed = tokensUsed
            )
        }
    }
    
    /**
     * Generate using Google Gemini API
     */
    private fun generateGemini(prompt: String): LLMResponse {
        val apiKey = config.geminiApiKey 
            ?: throw IllegalStateException("Gemini API key not configured")
        
        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", config.temperature)
                put("topP", config.topP)
                put("maxOutputTokens", config.maxTokensFactual)
            })
        }.toString()
        
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Gemini API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from Gemini")
            
            val json = JSONObject(responseBody)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            
            Log.d(TAG, "Gemini response received")
            
            return LLMResponse(
                text = text,
                modelName = model,
                isOffline = false,
                tokensUsed = null  // Gemini doesn't return token count in response
            )
        }
    }
    
    /**
     * Generate using Emergent Universal Key
     * (Routes to OpenAI/Anthropic/Gemini using single key)
     */
    private fun generateWithEmergentKey(prompt: String): LLMResponse {
        // This would use the Emergent integrations library
        // For now, fallback to OpenAI with the universal key
        val universalKey = getEmergentUniversalKey()
        
        // Use OpenAI format with universal key
        val requestBody = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", config.temperature)
            put("max_tokens", config.maxTokensFactual)
        }.toString()
        
        val request = Request.Builder()
            .url("https://api.emergent.com/v1/chat/completions")  // Emergent proxy endpoint
            .addHeader("Authorization", "Bearer $universalKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Emergent API error: ${response.code} ${response.message}")
            }
            
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from Emergent")
            
            val json = JSONObject(responseBody)
            val text = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
            
            return LLMResponse(
                text = text,
                modelName = model,
                isOffline = false,
                tokensUsed = null
            )
        }
    }
    
    /**
     * Get Emergent Universal LLM Key from environment
     */
    private fun getEmergentUniversalKey(): String {
        // This would call the emergent_integrations_manager
        // For now, return placeholder
        return System.getenv("EMERGENT_LLM_KEY") 
            ?: throw IllegalStateException("Emergent LLM key not found")
    }
}
