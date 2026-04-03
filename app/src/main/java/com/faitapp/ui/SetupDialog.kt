package com.faitapp.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.faitapp.R
import com.faitapp.core.FileRegistry

/**
 * Initial setup dialog for first run
 * Cyberpunk aesthetic with neon purple/green
 */
object SetupDialog {
    
    private const val TAG = "SetupDialog"
    private const val VRM_PICK_REQUEST = 1001
    private const val GGUF_PICK_REQUEST = 1002
    
    private var currentDialog: AlertDialog? = null
    private var fileRegistry: FileRegistry? = null
    
    fun showInitialSetup(activity: Activity, onComplete: () -> Unit) {
        fileRegistry = FileRegistry(activity)
        
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_setup, null)
        
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        currentDialog = dialog
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val titleText = dialogView.findViewById<TextView>(R.id.setupTitle)
        val messageText = dialogView.findViewById<TextView>(R.id.setupMessage)
        val vrmButton = dialogView.findViewById<TextView>(R.id.uploadVrmButton)
        val ggufButton = dialogView.findViewById<TextView>(R.id.uploadGgufButton)
        val skipButton = dialogView.findViewById<TextView>(R.id.skipButton)
        val completeButton = dialogView.findViewById<TextView>(R.id.completeButton)
        
        val vrmStatus = dialogView.findViewById<TextView>(R.id.vrmStatus)
        val ggufStatus = dialogView.findViewById<TextView>(R.id.ggufStatus)
        
        titleText.text = "⚙️ INITIAL SYSTEM CONFIGURATION"
        messageText.text = """
            Configure your AI companion:
            
            • VRM Avatar: Your companion's 3D appearance
            • GGUF Model: Local AI brain (optional - will use online if skipped)
            
            Both can be configured later from settings.
        """.trimIndent()
        
        // Check existing files
        updateFileStatus(vrmStatus, ggufStatus)
        
        vrmButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "model/gltf-binary",
                    "application/octet-stream"
                ))
            }
            activity.startActivityForResult(
                Intent.createChooser(intent, "Select VRM Avatar"),
                VRM_PICK_REQUEST
            )
        }
        
        ggufButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            activity.startActivityForResult(
                Intent.createChooser(intent, "Select GGUF Model"),
                GGUF_PICK_REQUEST
            )
        }
        
        skipButton.setOnClickListener {
            Log.d(TAG, "Setup skipped - will use online mode")
            markSetupComplete(activity)
            dialog.dismiss()
            onComplete()
        }
        
        completeButton.setOnClickListener {
            Log.d(TAG, "Setup completed")
            markSetupComplete(activity)
            dialog.dismiss()
            onComplete()
        }
        
        dialog.show()
    }
    
    fun handleActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
        onFileSelected: () -> Unit
    ) {
        if (resultCode != Activity.RESULT_OK || data == null) return
        
        val uri = data.data ?: return
        
        when (requestCode) {
            VRM_PICK_REQUEST -> {
                Log.d(TAG, "VRM file selected: $uri")
                val path = fileRegistry?.saveVrmFile(uri)
                if (path != null) {
                    Toast.makeText(activity, "VRM avatar saved!", Toast.LENGTH_SHORT).show()
                    onFileSelected()
                } else {
                    Toast.makeText(activity, "Failed to save VRM file", Toast.LENGTH_SHORT).show()
                }
            }
            GGUF_PICK_REQUEST -> {
                Log.d(TAG, "GGUF file selected: $uri")
                val path = fileRegistry?.saveGgufFile(uri)
                if (path != null) {
                    Toast.makeText(activity, "GGUF model saved!", Toast.LENGTH_SHORT).show()
                    onFileSelected()
                } else {
                    Toast.makeText(activity, "Failed to save GGUF file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun updateFileStatus(vrmStatus: TextView, ggufStatus: TextView) {
        val hasVrm = fileRegistry?.getVrmPath() != null
        val hasGguf = fileRegistry?.getGgufPath() != null
        
        vrmStatus.text = if (hasVrm) "✓ VRM Loaded" else "○ Not Set"
        vrmStatus.setTextColor(if (hasVrm) Color.parseColor("#00FF00") else Color.parseColor("#666666"))
        
        ggufStatus.text = if (hasGguf) "✓ GGUF Loaded" else "○ Optional"
        ggufStatus.setTextColor(if (hasGguf) Color.parseColor("#00FF00") else Color.parseColor("#666666"))
    }
    
    private fun markSetupComplete(context: Context) {
        val prefs = context.getSharedPreferences("FaitPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("setup_complete", true).apply()
        Log.d(TAG, "Setup marked as complete")
    }
}
