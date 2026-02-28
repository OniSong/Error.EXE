package com.faitapp.core

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileRegistry(private val context: Context) {

    companion object {
        private const val TAG = "FileRegistry"
        private const val VRM_FILENAME = "avatar.vrm"
        private const val GGUF_FILENAME = "model.gguf"
        private const val PREFS_NAME = "FileRegistryPrefs"
        private const val KEY_VRM_PATH = "vrm_path"
        private const val KEY_GGUF_PATH = "gguf_path"
        private const val KEY_API_KEY = "api_key"
    }

    private val filesDir = context.filesDir

    fun saveFileToInternal(uri: Uri, fileName: String): String? {
        return try {
            Log.d(TAG, "Saving file to internal storage: $fileName")
            
            val inputStream = try {
                context.contentResolver.openInputStream(uri) ?: run {
                    Log.e(TAG, "Failed to open input stream for URI: $uri")
                    return null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening input stream: ${e.message}")
                return null
            }

            val file = File(filesDir, fileName)
            
            if (file.exists()) {
                val deleted = file.delete()
                Log.d(TAG, "Existing file deleted: $deleted")
            }

            val outputStream = try {
                FileOutputStream(file)
            } catch (e: Exception) {
                Log.e(TAG, "Error creating output stream: ${e.message}")
                inputStream.close()
                return null
            }

            return try {
                inputStream.copyTo(outputStream)
                outputStream.flush()
                Log.d(TAG, "File saved successfully: ${file.absolutePath}")
                file.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Error copying file: ${e.message}")
                null
            } finally {
                try {
                    inputStream.close()
                    outputStream.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing streams: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in saveFileToInternal: ${e.message}", e)
            null
        }
    }

    fun getVrmPath(): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val path = prefs.getString(KEY_VRM_PATH, null)
            
            if (path != null && File(path).exists()) {
                Log.d(TAG, "VRM path retrieved: $path")
                path
            } else {
                Log.w(TAG, "VRM file not found at stored path")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving VRM path: ${e.message}", e)
            null
        }
    }

    fun getGgufPath(): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val path = prefs.getString(KEY_GGUF_PATH, null)
            
            if (path != null && File(path).exists()) {
                Log.d(TAG, "GGUF path retrieved: $path")
                path
            } else {
                Log.w(TAG, "GGUF file not found at stored path")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving GGUF path: ${e.message}", e)
            null
        }
    }

    fun getApiKey(): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val key = prefs.getString(KEY_API_KEY, null)
            
            if (key != null && key.isNotEmpty()) {
                Log.d(TAG, "API key retrieved")
                key
            } else {
                Log.w(TAG, "API key not configured")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving API key: ${e.message}", e)
            null
        }
    }

    fun hasRequiredFiles(): Boolean {
        return try {
            val vrmExists = getVrmPath() != null
            val ggufExists = getGgufPath() != null
            val apiKeyExists = getApiKey() != null
            
            val allPresent = vrmExists && ggufExists && apiKeyExists
            Log.d(TAG, "Required files check - VRM: $vrmExists, GGUF: $ggufExists, API: $apiKeyExists")
            
            allPresent
        } catch (e: Exception) {
            Log.e(TAG, "Error checking required files: ${e.message}", e)
            false
        }
    }

    fun hasLocalModel(): Boolean {
        return try {
            val ggufExists = getGgufPath() != null
            Log.d(TAG, "Local model check: $ggufExists")
            ggufExists
        } catch (e: Exception) {
            Log.e(TAG, "Error checking local model: ${e.message}", e)
            false
        }
    }
}
