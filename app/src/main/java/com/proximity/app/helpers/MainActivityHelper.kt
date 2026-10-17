package com.proximity.app.helpers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.proximity.app.R
import com.proximity.app.SetupActivity
import com.proximity.app.TestConfigurationActivity
import com.proximity.app.constants.AppConstants
import com.proximity.app.managers.CallManager
import com.proximity.app.managers.PermissionManager
import com.proximity.app.payment.PaymentInputValidator
import com.proximity.app.payment.Upi123CallStringBuilder
import com.proximity.app.payment.messageFor
import com.proximity.app.states.PaymentState

class MainActivityHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "MainActivityHelper"
    }

    // Managers
    private var callManager: CallManager? = null
    private var permissionManager: PermissionManager? = null

    interface UICallback {
        fun showToast(message: String)
        fun updatePaymentState(paymentState: PaymentState)
        fun navigateToSetup()
        fun navigateToTestConfiguration()
        fun finishActivity()
        fun showOverlayPermissionExplanation()
        fun launchQRScanner(intent: Intent)
        fun requestPhonePermissions()
    }
    private fun openQRScanner() {
        val intent = Intent(context, com.proximity.app.features.qr_scanner.presentation.QRScannerActivity::class.java)
        uiCallback.launchQRScanner(intent)
    }
    fun startQRScanning() {
        Log.d(TAG, "=== STARTING QR SCANNING ===")

        SetupHelper.getScanToPayBlockedMessage(context)?.let { message ->
            Log.d(TAG, "Scan to pay blocked: $message")
            uiCallback.showToast(message)
            return
        }
        if (permissionManager?.hasPhonePermissions() != true) {
            Log.d(TAG, "Phone permissions not granted, requesting...")
            uiCallback.showToast(context.getString(R.string.status_requesting_permissions))
            uiCallback.requestPhonePermissions()
            return
        }

        Log.d(TAG, "Phone permissions granted, opening QR scanner")
        uiCallback.showToast(context.getString(R.string.status_opening_scanner))
        openQRScanner()
    }
    fun initialize() {
        try {
            callManager = CallManager(context)
            permissionManager = PermissionManager(context as Activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization: ${e.message}")
        }
    }
    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_SETUP_COMPLETED, false)
    }
    fun isTestCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_TEST_COMPLETED, false)
    }
    @Suppress("ReturnCount")
    private fun isTransferInputValid(phoneNumber: String, amount: String): Boolean {
        if (phoneNumber.isBlank() || amount.isBlank()) {
            uiCallback.showToast(context.getString(R.string.error_enter_phone_and_amount))
            return false
        }
        if (!PaymentInputValidator.isValidPhoneNumber(phoneNumber)) {
            uiCallback.showToast(context.getString(R.string.error_invalid_phone_number))
            return false
        }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue < AppConstants.MIN_AMOUNT_VALUE) {
            uiCallback.showToast(context.getString(R.string.error_invalid_amount))
            return false
        }
        if (amountValue > AppConstants.UPI123PAY_MAX_AMOUNT) {
            uiCallback.showToast(
                Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context)
            )
            return false
        }
        return true
    }

    fun initiateTransfer(phoneNumber: String, amount: String) {
        Log.d(TAG, "Initiating transfer")

        if (!isTransferInputValid(phoneNumber, amount)) return
        if (permissionManager?.hasPhonePermissions() != true) {
            uiCallback.showToast(context.getString(R.string.error_phone_permission_required))
            uiCallback.requestPhonePermissions()
            return
        }

        if (permissionManager?.checkOverlayPermission() != true || !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Overlay permission not granted, showing explanation dialog")
            uiCallback.showOverlayPermissionExplanation()
            return
        }

        warnIfVoiceSimMismatch()

                val intent = Intent(context, com.proximity.app.ui.activities.PaymentGuidanceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(com.proximity.app.ui.activities.PaymentGuidanceActivity.EXTRA_PHONE_NUMBER, phoneNumber)
            putExtra(com.proximity.app.ui.activities.PaymentGuidanceActivity.EXTRA_AMOUNT, amount)
        }
        context.startActivity(intent)
    }

    private fun warnIfVoiceSimMismatch() {
        try {
            val selectedCarrier = context
                .getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString("selected_primary_sim", "") ?: ""
            if (selectedCarrier.isEmpty()) return

            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? android.telephony.SubscriptionManager ?: return
            val voiceSubId = android.telephony.SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (voiceSubId == android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
            if ((subscriptionManager.activeSubscriptionInfoCount) < 2) return

            val voiceCarrier = subscriptionManager.getActiveSubscriptionInfo(voiceSubId)
                ?.carrierName?.toString()?.lowercase() ?: return

            val matches = when (selectedCarrier) {
                "vodafone" -> voiceCarrier.contains("vodafone") || voiceCarrier.contains("vi")
                else -> voiceCarrier.contains(selectedCarrier)
            }
            if (!matches) {
                uiCallback.showToast(
                    context.getString(
                        R.string.warn_voice_sim_mismatch,
                        voiceCarrier,
                        selectedCarrier.replaceFirstChar { it.uppercase() }
                    )
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot check SIM mismatch: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "SIM mismatch check failed: ${e.message}")
        }
    }
    fun onPause() = Unit

    fun onStop() = Unit

    fun onResume() = Unit

    fun onDestroy() {
        try {
            try {
                callManager?.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up CallManager: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    fun navigateToSetup() {
        val intent = Intent(context, SetupActivity::class.java)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ context.startActivity(intent) }, 1000)
    }
    fun navigateToTestConfiguration() {
        val intent = Intent(context, TestConfigurationActivity::class.java)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ context.startActivity(intent) }, 1000)
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
    }
}


