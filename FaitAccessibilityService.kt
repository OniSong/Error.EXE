package com.faitapp.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class FaitAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FaitAccessibility"
        var isRunning = false

        private val FORBIDDEN_APPS = listOf(
            "com.tiktok",
            "com.instagram.android",
            "com.snapchat.android"
        )
    }

    private val appHistory = mutableListOf<Pair<String, Long>>()
    private val historyLock = Any()
    private var lastActiveApp = ""

    override fun onServiceConnected() {
        try {
            super.onServiceConnected()
            
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                canRetrieveWindowContent = true
            }
            
            setServiceInfo(info)
            isRunning = true
            Log.d(TAG, "Fait Accessibility Service connected and configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
            isRunning = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) {
            Log.w(TAG, "Received null accessibility event")
            return
        }
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(event)
                }
                else -> {
                    Log.v(TAG, "Other accessibility event: ${event.eventType}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event: ${e.message}", e)
        }
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            
            if (packageName == "android" || packageName.isEmpty()) return
            
            synchronized(historyLock) {
                if (packageName != lastActiveApp) {
                    appHistory.add(Pair(packageName, System.currentTimeMillis()))
                    lastActiveApp = packageName
                    
                    val oneHourAgo = System.currentTimeMillis() - 3600000
                    appHistory.removeAll { it.second < oneHourAgo }
                }
            }
            
            Log.d(TAG, "Active app: $packageName")
            checkForbiddenApp(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleWindowStateChanged: ${e.message}", e)
        }
    }

    private fun checkForbiddenApp(packageName: String) {
        try {
            if (FORBIDDEN_APPS.contains(packageName)) {
                Log.w(TAG, "Forbidden app detected: $packageName")
                
                if (performGlobalAction(GLOBAL_ACTION_HOME)) {
                    Log.d(TAG, "Successfully forced return to home")
                } else {
                    Log.e(TAG, "Failed to force return to home")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking forbidden app: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        try {
            Log.d(TAG, "Fait Accessibility Service interrupted")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onInterrupt: ${e.message}")
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            isRunning = false
            synchronized(historyLock) {
                appHistory.clear()
            }
            Log.d(TAG, "Fait Accessibility Service destroyed cleanly")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            isRunning = false
            Log.d(TAG, "Fait Accessibility Service unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onUnbind: ${e.message}")
        }
        return super.onUnbind(intent)
    }
}
