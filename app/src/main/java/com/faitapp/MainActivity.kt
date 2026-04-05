package com.faitapp

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.faitapp.core.FileRegistry
import com.faitapp.services.FaitOverlayService
import com.faitapp.ui.PermissionDialog
import com.faitapp.ui.SetupDialog

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val VRM_PICK_REQUEST = 1001
        const val GGUF_PICK_REQUEST = 1002
    }

    // Keep a reference to the FileRegistry so handleActivityResult always has one
    private lateinit var fileRegistry: FileRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            fileRegistry = FileRegistry(this)
            Log.d(TAG, "MainActivity created")
            checkPermissionsAndSetup()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasOverlayPermission()) {
            checkInitialSetup()
        }
    }

    private fun checkPermissionsAndSetup() {
        if (!hasOverlayPermission()) {
            PermissionDialog.showOverlayPermissionDialog(this) {
                Log.d(TAG, "User opened overlay settings")
            }
        } else {
            checkInitialSetup()
        }
    }

    private fun checkInitialSetup() {
        val prefs = getSharedPreferences("FaitPrefs", MODE_PRIVATE)
        val setupComplete = prefs.getBoolean("setup_complete", false)

        if (!setupComplete) {
            // Pass 'this' so SetupDialog can use our onActivityResult
            SetupDialog.showInitialSetup(this) {
                Log.d(TAG, "Setup complete — starting overlay service")
                startOverlayService()
            }
        } else {
            startOverlayService()
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this)
        else true
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, FaitOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d(TAG, "FaitOverlayService started")
        } catch (e: Exception) {
            Log.e(TAG, "startOverlayService error: ${e.message}", e)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data == null) return
        val uri = data.data ?: return

        when (requestCode) {
            VRM_PICK_REQUEST -> {
                Log.d(TAG, "VRM file selected: $uri")
                val path = fileRegistry.saveVrmFile(uri)
                if (path != null) {
                    Log.d(TAG, "VRM saved to: $path")
                    // Mark setup complete now that VRM is present
                    getSharedPreferences("FaitPrefs", MODE_PRIVATE)
                        .edit().putBoolean("setup_complete", true).apply()
                    // Tell the running overlay service to reload the avatar
                    sendBroadcast(Intent("com.faitapp.RELOAD_AVATAR"))
                    // Also restart the service to pick up the new file
                    startOverlayService()
                } else {
                    Log.e(TAG, "VRM save failed")
                }
            }
            GGUF_PICK_REQUEST -> {
                Log.d(TAG, "GGUF file selected: $uri")
                val path = fileRegistry.saveGgufFile(uri)
                if (path != null) {
                    Log.d(TAG, "GGUF saved to: $path")
                }
            }
            else -> {
                // Delegate to SetupDialog for any other request codes it handles
                SetupDialog.handleActivityResult(this, requestCode, resultCode, data) {
                    Log.d(TAG, "File selected via SetupDialog")
                }
            }
        }
    }
}
