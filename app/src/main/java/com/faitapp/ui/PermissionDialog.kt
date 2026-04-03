package com.faitapp.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.TextView
import com.faitapp.R

/**
 * Permission request dialog with cyberpunk aesthetic
 */
object PermissionDialog {
    
    fun showOverlayPermissionDialog(context: Context, onSettingsOpened: () -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_permission, null)
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        val titleText = dialogView.findViewById<TextView>(R.id.permissionTitle)
        val messageText = dialogView.findViewById<TextView>(R.id.permissionMessage)
        val grantButton = dialogView.findViewById<TextView>(R.id.grantButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelButton)
        
        titleText.text = "⚠️ SYSTEM ACCESS REQUIRED"
        messageText.text = """
            Error.EXE requires Display Over Other Apps permission to function.
            
            This allows your AI companion to appear as an overlay on your screen, providing real-time assistance while you use other apps.
            
            Your privacy is protected - this permission only allows the overlay window.
        """.trimIndent()
        
        grantButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            onSettingsOpened()
            dialog.dismiss()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
}
