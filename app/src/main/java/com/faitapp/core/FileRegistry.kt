package com.faitapp.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileRegistry(private val context: Context) {

    companion object {
        private const val TAG = "FileRegistry"

        private val SUPPORTED_VRM_EXTENSIONS = setOf(".vrm", ".glb", ".gltf", ".vrm.glb")
        private val SUPPORTED_GGUF_EXTENSIONS = setOf(".gguf")

        private const val PREFS_NAME = "FileRegistryPrefs"
        private const val KEY_VRM_PATH = "vrm_path"
        private const val KEY_VRM_FILENAME = "vrm_filename"
        private const val KEY_GGUF_PATH = "gguf_path"
        private const val KEY_API_KEY = "api_key"
    }

    private val filesDir = context.filesDir

    private fun getFileExtension(filename: String): String {
        val lower = filename.lowercase()
        if (lower.endsWith(".vrm.glb")) return ".vrm.glb"
        val dot = filename.lastIndexOf('.')
        return if (dot > 0) filename.substring(dot).lowercase() else ""
    }

    private fun getOriginalFileName(uri: Uri): String? {
        return try {
            var result: String? = null
            if (uri.scheme == "content") {
                val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) result = it.getString(idx)
                    }
                }
            }
            if (result == null) {
                result = uri.path
                val cut = result?.lastIndexOf('/') ?: -1
                if (cut != -1) result = result!!.substring(cut + 1)
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "getOriginalFileName error: ${e.message}", e)
            null
        }
    }

    fun saveVrmFile(uri: Uri): String? {
        return try {
            val originalName = getOriginalFileName(uri) ?: run {
                Log.e(TAG, "Could not extract filename from URI")
                return null
            }
            val ext = getFileExtension(originalName)
            if (ext !in SUPPORTED_VRM_EXTENSIONS) {
                Log.e(TAG, "Unsupported VRM extension: $ext (file: $originalName)")
                return null
            }
            val savedPath = saveFileToInternal(uri, originalName) ?: return null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
                putString(KEY_VRM_PATH, savedPath)
                putString(KEY_VRM_FILENAME, originalName)
                apply()
            }
            Log.d(TAG, "VRM saved: $savedPath")
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "saveVrmFile error: ${e.message}", e)
            null
        }
    }

    fun saveGgufFile(uri: Uri): String? {
        return try {
            val originalName = getOriginalFileName(uri) ?: return null
            val ext = getFileExtension(originalName)
            if (ext !in SUPPORTED_GGUF_EXTENSIONS) {
                Log.e(TAG, "Unsupported GGUF extension: $ext")
                return null
            }
            val savedPath = saveFileToInternal(uri, "model.gguf") ?: return null
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_GGUF_PATH, savedPath).apply()
            Log.d(TAG, "GGUF saved: $savedPath")
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "saveGgufFile error: ${e.message}", e)
            null
        }
    }

    fun saveFileToInternal(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                Log.e(TAG, "Cannot open input stream for URI: $uri")
                return null
            }
            val file = File(filesDir, fileName)
            if (file.exists()) file.delete()
            val outputStream = FileOutputStream(file)
            try {
                inputStream.copyTo(outputStream)
                outputStream.flush()
                Log.d(TAG, "File saved: ${file.absolutePath} (${file.length() / 1024} KB)")
                file.absolutePath
            } catch (e: IOException) {
                Log.e(TAG, "Copy error: ${e.message}")
                null
            } finally {
                inputStream.close()
                outputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveFileToInternal error: ${e.message}", e)
            null
        }
    }

    fun getVrmPath(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_VRM_PATH, null)
        if (path != null && File(path).exists()) return path
        // Legacy fallback
        val legacy = File(filesDir, "avatar.vrm")
        if (legacy.exists()) {
            prefs.edit().putString(KEY_VRM_PATH, legacy.absolutePath)
                .putString(KEY_VRM_FILENAME, "avatar.vrm").apply()
            return legacy.absolutePath
        }
        return null
    }

    fun getVrmFileName(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VRM_FILENAME, null)
    }

    fun getGgufPath(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_GGUF_PATH, null)
        return if (path != null && File(path).exists()) path else null
    }

    fun getApiKey(): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_API_KEY, null)
        return if (!key.isNullOrEmpty()) key else null
    }

    fun saveApiKey(key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_KEY, key).apply()
    }

    /** 
     * Only requires VRM to be present — GGUF and API key are optional (online fallback).
     * Previously required all three which made setup impossible to complete.
     */
    fun hasRequiredFiles(): Boolean {
        val hasVrm = getVrmPath() != null
        Log.d(TAG, "hasRequiredFiles — VRM: $hasVrm")
        return hasVrm
    }

    fun hasLocalModel(): Boolean = getGgufPath() != null
}
