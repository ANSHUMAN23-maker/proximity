// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import com.proximity.app.AppCore
import com.proximity.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.IdentityHashMap

class UssdDialogReaderService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val TAG = "UssdDialogReaderService"
        private const val MAX_NODE_DEPTH = 24

        @Volatile
        var ussdPaymentActive: Boolean = false

        @Volatile
        var latestResult: UssdDetectionResult? = null

        @Volatile
        var ussdCallback: ((UssdDetectionResult) -> Unit)? = null

        @Volatile
        var isServiceConnected: Boolean = false

        @Volatile
        private var instance: UssdDialogReaderService? = null

        fun reset() {
            latestResult = null
            ussdCallback = null
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "UssdDialogReaderService state reset")
            }
        }

        fun disableService() {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "disableService() called - disabling accessibility in system settings")
                }
                instance?.disableSelf()
                instance = null
                latestResult = null
                ussdCallback = null
                ussdPaymentActive = false
                isServiceConnected = false
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Error in disableService()", e)
                }
            }
        }

        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                val services = manager?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    ?: emptyList()
                val enabledInManager = services.any { service ->
                    service.id.contains("UssdDialogReaderService", ignoreCase = true)
                }
                if (enabledInManager) return true

                val enabledServicesSetting = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                enabledServicesSetting.contains("UssdDialogReaderService", ignoreCase = true)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Failed to check accessibility service status", e)
                }
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceConnected = true

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
                AccessibilityServiceInfo.FEEDBACK_HAPTIC or
                AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        serviceInfo = info
    }

    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)
        serviceScope.cancel()
        instance = null
        isServiceConnected = false
        ussdPaymentActive = false
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        disableService()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        disableService()
    }

    override fun onInterrupt() {
        ussdPaymentActive = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !ussdPaymentActive) return
        isServiceConnected = true

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val texts = mutableListOf<String>()
        event.text?.forEach { item ->
            if (!item.isNullOrBlank()) {
                texts.add(item.toString())
            }
        }

        val visitedNodes = Collections.newSetFromMap(IdentityHashMap<AccessibilityNodeInfo, Boolean>())

        try {
            event.source?.let { source ->
                try {
                    collectTextRecursive(source, texts, visitedNodes, depth = 0)
                } finally {
                    recycleNodeLegacy(source)
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Could not inspect event source", e)
        }

        try {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                try {
                    collectTextRecursive(activeRoot, texts, visitedNodes, depth = 0)
                } finally {
                    recycleNodeLegacy(activeRoot)
                }
            } else {
                windows?.forEach { window ->
                    window.root?.let { windowRoot ->
                        try {
                            collectTextRecursive(windowRoot, texts, visitedNodes, depth = 0)
                        } finally {
                            recycleNodeLegacy(windowRoot)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Could not inspect windows", e)
        }

        val distinctTexts = texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinctTexts.isEmpty()) return

        val dialogText = distinctTexts.joinToString(" | ")
        val parsed = UssdDialogAnalyzer.parsePaymentResult(dialogText)
        val merged = parsed.mergeWithPrevious(latestResult)
        latestResult = merged

        if (merged.status != UssdDetectionStatus.UNKNOWN) {
            ussdCallback?.invoke(merged)
        }

        val app = applicationContext as? AppCore
        if (app != null && app.services.paymentLifecycleManager.isUssdPaymentInFlight()) {
            serviceScope.launch {
                app.services.paymentLifecycleManager.ingestUssdAccessibilityEvent(distinctTexts)
            }
        }
    }

    private fun collectTextRecursive(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        visited: MutableSet<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (node == null || depth > MAX_NODE_DEPTH) return
        if (!visited.add(node)) return
        // NEVER capture password / PIN input fields
        if (node.isPassword) return

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            try {
                collectTextRecursive(child, texts, visited, depth + 1)
            } finally {
                recycleNodeLegacy(child)
            }
        }
    }

    private fun recycleNodeLegacy(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }
}
