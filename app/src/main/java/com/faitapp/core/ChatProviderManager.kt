package com.faitapp.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatProviderManager(private val context: Context) {

    companion object {
        private const val TAG = "ChatProviderManager"
    }

    private val personaEngine = AiriPersonaEngine()
    private val fileRegistry = FileRegistry(context)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var failureCount = 0

    fun getResponse(userInput: String, callback: (String) -> Unit) {
        scope.launch {
            try {
                Log.d(TAG, "Processing user input: ${userInput.take(50)}...")
                
                val isOnline = isNetworkAvailable()
                val hasLocalModel = fileRegistry.hasLocalModel()
                val queryHash = userInput.hashCode().toString()
                val retryCount = personaEngine.trackQueryRetry(queryHash)
                
                Log.d(TAG, "Network status - Online: $isOnline, Local Model: $hasLocalModel")
                
                when {
                    isOnline -> {
                        Log.d(TAG, "Network available - attempting cloud provider")
                        try {
                            callCloudProvider(userInput, callback)
                        } catch (e: Exception) {
                            Log.e(TAG, "Cloud provider failed: ${e.message}")
                            handleCloudFailure(userInput, hasLocalModel, callback)
                        }
                    }
                    hasLocalModel -> {
                        Log.d(TAG, "Offline with local model - attempting local inference")
                        try {
                            executeLocalInference(userInput, callback)
                        } catch (e: Exception) {
                            Log.e(TAG, "Local inference failed: ${e.message}")
                            handleLocalFailure(callback)
                        }
                    }
                    else -> {
                        Log.d(TAG, "Offline without local model - using persona fallback")
                        handleCompleteFailure(callback)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in getResponse: ${e.message}", e)
                callback(personaEngine.wrapError(e))
            }
        }
    }

    private fun callCloudProvider(userInput: String, callback: (String) -> Unit) {
        try {
            val apiKey = fileRegistry.getApiKey()
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "API key not configured")
                callback(personaEngine.getFallbackResponse())
                return
            }
            
            Log.d(TAG, "Calling cloud provider with configured API key")
            val mockResponse = "Cloud response for: ${userInput.take(30)}..."
            failureCount = 0
            callback(mockResponse)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in callCloudProvider: ${e.message}", e)
            throw e
        }
    }

    private fun executeLocalInference(userInput: String, callback: (String) -> Unit) {
        try {
            val ggufPath = fileRegistry.getGgufPath()
            if (ggufPath == null) {
                Log.e(TAG, "GGUF path not available")
                throw Exception("GGUF model path not configured")
            }
            
            Log.d(TAG, "Executing local inference with model at: $ggufPath")
            val mockResponse = "Local inference result for: ${userInput.take(30)}..."
            failureCount = 0
            callback(mockResponse)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in executeLocalInference: ${e.message}", e)
            throw e
        }
    }

    private fun handleCloudFailure(
        userInput: String,
        hasLocalModel: Boolean,
        callback: (String) -> Unit
    ) {
        try {
            failureCount++
            Log.d(TAG, "Handling cloud failure. Local model available: $hasLocalModel")
            
            when {
                hasLocalModel -> {
                    Log.d(TAG, "Falling back to local model")
                    try {
                        executeLocalInference(userInput, callback)
                    } catch (e: Exception) {
                        Log.e(TAG, "Local fallback also failed: ${e.message}")
                        handleLocalFailure(callback)
                    }
                }
                else -> {
                    Log.d(TAG, "No local model available")
                    handleCompleteFailure(callback)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleCloudFailure: ${e.message}", e)
            callback(personaEngine.wrapError(e, "cloud fallback"))
        }
    }

    private fun handleLocalFailure(callback: (String) -> Unit) {
        try {
            Log.d(TAG, "Local model failed")
            failureCount++
            val response = personaEngine.getFallbackResponse(isRepeatedQuery = false)
            callback(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleLocalFailure: ${e.message}", e)
            callback(personaEngine.wrapError(e, "local failure handling"))
        }
    }

    private fun handleCompleteFailure(callback: (String) -> Unit) {
        try {
            Log.d(TAG, "Complete system failure")
            failureCount++
            val response = personaEngine.getFallbackResponse(isRepeatedQuery = false)
            callback(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleCompleteFailure: ${e.message}", e)
            callback("System recovery unavailable. Please restart the application.")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                Log.w(TAG, "ConnectivityManager not available")
                return false
            }
            
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                Log.d(TAG, "No active network")
                return false
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                Log.d(TAG, "Network capabilities null")
                return false
            }
            
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            Log.d(TAG, "Network availability: $hasInternet")
            hasInternet
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability: ${e.message}", e)
            false
        }
    }

    fun clearFailureState() {
        try {
            failureCount = 0
            personaEngine.clearQueryRetries()
            Log.d(TAG, "Failure state cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing failure state: ${e.message}")
        }
    }
}
