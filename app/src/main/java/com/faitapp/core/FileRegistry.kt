package com.faitapp.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileRegistry(private val context: Context) {

    companion object {
        private const val TAG = "FileRegistry"
        
        // Supported VRM file extensions
        private val SUPPORTED_VRM_EXTENSIONS = setOf(
            ".vrm",
            ".glb", 
            ".gltf",
            ".vrm.glb"  // Double extension support
        )
        
        // Supported GGUF extensions
        private val SUPPORTED_GGUF_EXTENSIONS = setOf(".gguf")
        
        // MIME types for validation
        private val SUPPORTED_MIME_TYPES = setOf(
            "model/gltf-binary",
            "model/gltf+json",
            "application/octet-stream"
        )
        
        private const val GGUF_FILENAME = "model.gguf"
        private const val PREFS_NAME = "FileRegistryPrefs"
        private const val KEY_VRM_PATH = "vrm_path"
        private const val KEY_VRM_FILENAME = "vrm_filename"
        private const val KEY_GGUF_PATH = "gguf_path"
        private const val KEY_API_KEY = "api_key"
    }

    private val filesDir = context.filesDir

    /**
     * Extract file extension from filename, supporting double extensions like .vrm.glb
     * @param filename The filename to extract extension from
     * @return The extension (e.g., ".vrm.glb", ".glb", ".gltf") or empty string if none
     */
    private fun getFileExtension(filename: String): String {
        return try {
            val lowerName = filename.lowercase()
            
            // Check for double extension first (.vrm.glb)
            if (lowerName.endsWith(".vrm.glb")) {
                return ".vrm.glb"
            }
            
            // Check for standard single extensions
            val lastDotIndex = filename.lastIndexOf('.')
            if (lastDotIndex > 0 && lastDotIndex < filename.length - 1) {
                return filename.substring(lastDotIndex).lowercase()
            }
            
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting file extension: ${e.message}")
            ""
        }
    }

    /**
     * Extract original filename from URI using ContentResolver
     * @param uri The URI to extract filename from
     * @return Original filename or null if unavailable
     */
    private fun getOriginalFileName(uri: Uri): String? {
        return try {
            var result: String? = null
            
            if (uri.scheme == "content") {
                val cursor: Cursor? = context.contentResolver.query(
                    uri, null, null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            result = it.getString(nameIndex)
                        }
                    }
                }
            }
            
            // Fallback to URI path if content resolver fails
            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/') ?: -1
                if (cut != -1 && result != null) {
                    result = result!!.substring(cut + 1)
                }
            }
            
            Log.d(TAG, "Extracted filename: $result from URI: $uri")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error getting original filename: ${e.message}", e)
            null
        }
    }

    /**
     * Validate if file is a supported VRM format
     * @param filename The filename to validate
     * @return true if file has supported VRM extension
     */
    private fun isValidVrmFile(filename: String): Boolean {
        val extension = getFileExtension(filename)
        val isValid = SUPPORTED_VRM_EXTENSIONS.contains(extension)
        Log.d(TAG, "Validating VRM file: $filename, Extension: $extension, Valid: $isValid")
        return isValid
    }

    /**
     * Validate if file is a supported GGUF format
     * @param filename The filename to validate
     * @return true if file has supported GGUF extension
     */
    private fun isValidGgufFile(filename: String): Boolean {
        val extension = getFileExtension(filename)
        val isValid = SUPPORTED_GGUF_EXTENSIONS.contains(extension)
        Log.d(TAG, "Validating GGUF file: $filename, Extension: $extension, Valid: $isValid")
        return isValid
    }

    /**
     * Validate MIME type of file
     * @param uri The URI of the file
     * @return true if MIME type is supported
     */
    private fun isValidMimeType(uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            val isValid = mimeType != null && SUPPORTED_MIME_TYPES.contains(mimeType)
            Log.d(TAG, "MIME type validation: $mimeType, Valid: $isValid")
            isValid || mimeType == null // Allow null MIME types (fallback to extension check)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking MIME type: ${e.message}")
            true // Allow if MIME check fails (rely on extension)
        }
    }


    /**
     * Save a VRM file from URI to internal storage with original filename preservation
     * Supports .vrm, .glb, .gltf, and .vrm.glb formats
     * @param uri URI of the file to save
     * @return Absolute path of saved file or null on failure
     */
    fun saveVrmFile(uri: Uri): String? {
        return try {
            Log.d(TAG, "Saving VRM file from URI: $uri")
            
            // Get original filename
            val originalName = getOriginalFileName(uri)
            if (originalName == null) {
                Log.e(TAG, "Failed to extract filename from URI")
                return null
            }
            
            // Validate file extension
            if (!isValidVrmFile(originalName)) {
                val extension = getFileExtension(originalName)
                Log.e(TAG, "Invalid VRM file format: $originalName (Extension: $extension)")
                Log.e(TAG, "Supported formats: ${SUPPORTED_VRM_EXTENSIONS.joinToString(", ")}")
                return null
            }
            
            // Validate MIME type (optional check)
            if (!isValidMimeType(uri)) {
                Log.w(TAG, "MIME type validation failed for: $originalName (proceeding anyway)")
            }
            
            // Save file with original filename
            val savedPath = saveFileToInternal(uri, originalName)
            
            if (savedPath != null) {
                // Store path and filename in SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString(KEY_VRM_PATH, savedPath)
                    putString(KEY_VRM_FILENAME, originalName)
                    apply()
                }
                Log.d(TAG, "VRM file saved successfully: $savedPath")
            }
            
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving VRM file: ${e.message}", e)
            null
        }
    }

    /**
     * Save a GGUF model file from URI to internal storage
     * @param uri URI of the GGUF file to save
     * @return Absolute path of saved file or null on failure
     */
    fun saveGgufFile(uri: Uri): String? {
        return try {
            Log.d(TAG, "Saving GGUF file from URI: $uri")
            
            // Get original filename
            val originalName = getOriginalFileName(uri)
            if (originalName == null) {
                Log.e(TAG, "Failed to extract filename from URI")
                return null
            }
            
            // Validate file extension
            if (!isValidGgufFile(originalName)) {
                val extension = getFileExtension(originalName)
                Log.e(TAG, "Invalid GGUF file format: $originalName (Extension: $extension)")
                return null
            }
            
            // Save file with standard GGUF filename
            val savedPath = saveFileToInternal(uri, GGUF_FILENAME)
            
            if (savedPath != null) {
                // Store path in SharedPreferences
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(KEY_GGUF_PATH, savedPath).apply()
                Log.d(TAG, "GGUF file saved successfully: $savedPath")
            }
            
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving GGUF file: ${e.message}", e)
            null
        }
    }

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
                // Backward compatibility: check for legacy "avatar.vrm" file
                val legacyFile = File(filesDir, "avatar.vrm")
                if (legacyFile.exists()) {
                    Log.d(TAG, "Found legacy VRM file: ${legacyFile.absolutePath}")
                    // Update preferences with legacy file path
                    prefs.edit().apply {
                        putString(KEY_VRM_PATH, legacyFile.absolutePath)
                        putString(KEY_VRM_FILENAME, "avatar.vrm")
                        apply()
                    }
                    legacyFile.absolutePath
                } else {
                    Log.w(TAG, "VRM file not found at stored path")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving VRM path: ${e.message}", e)
            null
        }
    }

    /**
     * Get the original filename of the saved VRM file
     * @return Filename (e.g., "FaitProto.vrm.glb") or null if not found
     */
    fun getVrmFileName(): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val filename = prefs.getString(KEY_VRM_FILENAME, null)
            
            if (filename != null) {
                Log.d(TAG, "VRM filename retrieved: $filename")
                filename
            } else {
                // Backward compatibility: return "avatar.vrm" if legacy file exists
                val legacyFile = File(filesDir, "avatar.vrm")
                if (legacyFile.exists()) {
                    "avatar.vrm"
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving VRM filename: ${e.message}", e)
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
