package com.faitapp.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hybrid LLM Provider
 * 
 * Intelligently switches between offline GGUF models and online cloud APIs based on:
 * - Network connectivity
 * - Model availability
 * - User preferences
 * - Performance requirements
 */
class HybridLLMProvider(
    private val context: Context,
    private val offlineModelPath: String?,
    private val onlineProvider: CloudProvider,
    private val onlineModel: String,
    private val config: DualLLMConfig
) : LLMProvider {
    
    companion object {
        private const val TAG = "HybridLLMProvider"
    }
    
    private val offlineProvider: OfflineLLMProvider? by lazy {
        if (offlineModelPath != null && offlineModelPath.isNotEmpty()) {
            OfflineLLMProvider(context, offlineModelPath)
        } else {
            Log.w(TAG, "No offline model path configured")
            null
        }
    }
    
    private val onlineProviderInstance: OnlineLLMProvider by lazy {
        OnlineLLMProvider(context, onlineProvider, onlineModel, config)
    }
    
    /**
     * Generate text using best available provider
     */
    override suspend fun generate(prompt: String): LLMResponse = withContext(Dispatchers.IO) {
        val isOnline = isNetworkAvailable()
        val hasOfflineModel = offlineProvider != null
        
        Log.d(TAG, "Hybrid generation - Online: $isOnline, HasOffline: $hasOfflineModel")
        
        // Try offline first (preferred for privacy and cost)
        if (hasOfflineModel) {
            try {
                Log.d(TAG, "Attempting offline generation")
                return@withContext offlineProvider!!.generate(prompt)
            } catch (e: Exception) {
                Log.w(TAG, "Offline generation failed: ${e.message}")
                
                if (!isOnline) {
                    // No network and offline failed - can't proceed
                    throw Exception("Offline model failed and no network available: ${e.message}")
                }
                
                // Fall through to online
                Log.d(TAG, "Falling back to online provider")
            }
        }
        
        // Use online provider
        if (isOnline) {
            try {
                Log.d(TAG, "Using online provider: $onlineProvider")
                return@withContext onlineProviderInstance.generate(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Online generation failed: ${e.message}", e)
                throw Exception("Both offline and online providers failed: ${e.message}")
            }
        } else {
            throw Exception("No network available and no offline model configured")
        }
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Get provider status for UI display
     */
    fun getStatus(): ProviderStatus {
        val isOnline = isNetworkAvailable()
        val hasOffline = offlineProvider != null
        
        val currentProvider = when {
            hasOffline -> "Offline GGUF (Preferred)"
            isOnline -> "Online $onlineProvider"
            else -> "None Available"
        }
        
        val fallbackAvailable = hasOffline && isOnline
        
        return ProviderStatus(
            currentProvider = currentProvider,
            isOnline = isOnline,
            hasOfflineModel = hasOffline,
            fallbackAvailable = fallbackAvailable
        )
    }
}

/**
 * Provider status information
 */
data class ProviderStatus(
    val currentProvider: String,
    val isOnline: Boolean,
    val hasOfflineModel: Boolean,
    val fallbackAvailable: Boolean
)
