package com.faitapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.faitapp.services.FaitOverlayService
import com.faitapp.ui.PermissionDialog
import com.faitapp.ui.SetupDialog

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_main)
            
            Log.d(TAG, "MainActivity created")
            
            // Check permission and setup
            checkPermissionsAndSetup()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }
    
    override fun onResume() {
        try {
            super.onResume()
            Log.d(TAG, "MainActivity resumed")
            
            // Re-check permission in case user granted it
            if (hasOverlayPermission()) {
                checkInitialSetup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}")
        }
    }
    
    private fun checkPermissionsAndSetup() {
        if (!hasOverlayPermission()) {
            Log.d(TAG, "Overlay permission not granted - showing permission dialog")
            PermissionDialog.showOverlayPermissionDialog(this) {
                Log.d(TAG, "User opened settings for overlay permission")
            }
        } else {
            Log.d(TAG, "Overlay permission already granted")
            checkInitialSetup()
        }
    }
    
    private fun checkInitialSetup() {
        val prefs = getSharedPreferences("FaitPrefs", MODE_PRIVATE)
        val setupComplete = prefs.getBoolean("setup_complete", false)
        
        if (!setupComplete) {
            Log.d(TAG, "First run - showing setup dialog")
            SetupDialog.showInitialSetup(this) {
                Log.d(TAG, "Setup complete - starting overlay service")
                startOverlayService()
            }
        } else {
            Log.d(TAG, "Setup already complete - starting overlay service")
            startOverlayService()
        }
    }
    
    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Permission not required on older Android versions
        }
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
            
            // Keep MainActivity open for now (can be minimized by user)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting overlay service: ${e.message}", e)
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        SetupDialog.handleActivityResult(this, requestCode, resultCode, data) {
            // File selected callback - could update UI here
            Log.d(TAG, "File selected in setup")
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            Log.d(TAG, "MainActivity destroyed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
    }
}
