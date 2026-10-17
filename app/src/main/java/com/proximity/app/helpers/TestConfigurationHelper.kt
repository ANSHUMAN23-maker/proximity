package com.proximity.app.helpers

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.proximity.app.R
import com.proximity.app.constants.AppConstants
import com.proximity.app.data.TestResults
import com.proximity.app.data.TestResultsManager
import com.proximity.app.managers.CallManager
import com.proximity.app.managers.CallType
import com.proximity.app.managers.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


class TestConfigurationHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "TestConfigurationHelper"
    }

    // Managers
    private lateinit var callManager: CallManager
    private lateinit var testResultsManager: TestResultsManager
    private lateinit var permissionManager: PermissionManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ussdTimeoutRunnable: Runnable? = null
    private var upi123ConfigDelayRunnable: Runnable? = null

    // Test state variables
    private var currentTestingType: CallType? = null

    // StateFlow for reactive UI updates (replaces polling loop)
    private val _testStates = MutableStateFlow(TestStates(
        ussdTesting = false,
        showUssdDialog = false,
        ussdTestCompleted = false,
        upi123Testing = false,
        upi123TestCompleted = false,
        voiceTesting = false,
        showVoiceDialog = false,
        voiceTestCompleted = false,
        showCallCompleteButton = false,
        ussdProgressMessage = "Initializing...",
        showUssdConfigurationOptions = false,
        showUpi123Dialog = false,
        showUpi123ConfigurationOptions = false
    ))
    val testStatesFlow: StateFlow<TestStates> = _testStates.asStateFlow()

    private fun emitTestStates() {
        _testStates.value = TestStates(
            ussdTesting = ussdTesting,
            showUssdDialog = showUssdDialog,
            ussdTestCompleted = ussdTestCompleted,
            upi123Testing = upi123Testing,
            upi123TestCompleted = upi123TestCompleted,
            voiceTesting = voiceTesting,
            showVoiceDialog = showVoiceDialog,
            voiceTestCompleted = voiceTestCompleted,
            showCallCompleteButton = showCallCompleteButton,
            ussdProgressMessage = ussdProgressMessage,
            showUssdConfigurationOptions = showUssdConfigurationOptions,
            showUpi123Dialog = showUpi123Dialog,
            showUpi123ConfigurationOptions = showUpi123ConfigurationOptions
        )
    }

    private var ussdTesting = false
    private var showUssdDialog = false
    private var ussdTestCompleted = false
    private var showCallCompleteButton = false
    private var upi123TestCompleted = false
    private var voiceTesting = false
    private var showVoiceDialog = false
    private var voiceTestCompleted = false
    private var upi123Testing = false
    private var showUpi123Dialog = false
    private var showUpi123ConfigurationOptions = false
    private var ussdProgressMessage = "Initializing..."
    private var showUssdConfigurationOptions = false

    interface UICallback {
        fun showToast(message: String)
        fun updateUssdTesting(isTesting: Boolean)
        fun updateUssdDialog(show: Boolean)
        fun updateUssdTestCompleted(completed: Boolean)
        fun updateUpi123Testing(isTesting: Boolean)
        fun updateUpi123TestCompleted(completed: Boolean)
        fun updateUpi123Dialog(show: Boolean)
        fun updateUpi123ConfigurationOptions(show: Boolean)
        fun updateVoiceTesting(isTesting: Boolean)
        fun updateVoiceDialog(show: Boolean)
        fun updateVoiceTestCompleted(completed: Boolean)
        fun updateCallCompleteButton(show: Boolean)
        fun updateUssdProgressMessage(message: String)
        fun updateUssdConfigurationOptions(show: Boolean)
        fun navigateToMain()

        fun requestPhonePermissions()
    }

    fun initialize() {
        callManager = CallManager(context)
        testResultsManager = TestResultsManager(context)
        permissionManager = PermissionManager(context as Activity)
    }

    fun cleanup() {
        cancelUssdTimeout()
        cancelUpi123ConfigDelay()
        if (::callManager.isInitialized) {
            callManager.cleanup()
        }
    }

    fun onPhonePermissionsResult(granted: Boolean) {
        if (granted) {
            Log.d(TAG, "All permissions granted")
            uiCallback.showToast(context.getString(R.string.status_permissions_granted_ussd))
        } else {
            Log.w(TAG, "Some permissions denied")
            uiCallback.showToast(context.getString(R.string.error_permissions_denied))
        }
    }

    fun initiateCall(callType: CallType) {
        // Guard: prevent duplicate dials
        if (callType == CallType.USSD && (ussdTesting || ussdTestCompleted)) {
            Log.d(TAG, "USSD already in progress or completed, ignoring duplicate dial")
            return
        }

        if (!permissionManager.hasPhonePermissions()) {
            Log.e(TAG, "Phone permissions not granted")
            uiCallback.showToast(context.getString(R.string.error_call_permission_required_ussd))
            uiCallback.requestPhonePermissions()
            return
        }

        val number = when (callType) {
            CallType.USSD -> "*99#"
            CallType.VOICE -> "1234567890"
            CallType.UPI123 -> "1234567890" // This should not be used anymore
            CallType.MANUAL_TRANSFER -> "1234567890"
        }

        when (callType) {
            CallType.USSD -> {
                Log.d(TAG, "Initiating USSD setup for *99# - will show dialog after 25 seconds")
                SetupHelper.setUserReportedUssdNotWorking(context, false)
                ussdTesting = true
                showUssdDialog = true
                ussdProgressMessage = "Dialing *99#..."
                uiCallback.updateUssdTesting(true)
                uiCallback.updateUssdDialog(true)
                uiCallback.updateUssdProgressMessage(ussdProgressMessage)
                emitTestStates()
                Log.d(TAG, "USSD state set - ussdTesting: $ussdTesting, showUssdDialog: $showUssdDialog")
            }
            CallType.VOICE -> {
                voiceTesting = true
                showVoiceDialog = true
                uiCallback.updateVoiceTesting(true)
                uiCallback.updateVoiceDialog(true)
                emitTestStates()
            }
            CallType.MANUAL_TRANSFER -> {
                // Manual transfer testing - similar to voice call
                voiceTesting = true
                showVoiceDialog = true
                uiCallback.updateVoiceTesting(true)
                uiCallback.updateVoiceDialog(true)
                emitTestStates()
            }
            CallType.UPI123 -> {
                Log.w(TAG, "UPI123 should use initiateUpi123Test() method instead of initiateCall()")
                return
            }
        }

        currentTestingType = callType
        if (callType == CallType.USSD) {
            startUssdTimeout()
        }

        callManager.initiateCall(
            context = context,
            phoneNumber = number,
            callType = callType,
            onCallEnded = { type ->
                when (type) {
                    CallType.VOICE -> {
                        voiceTesting = false
                        showVoiceDialog = false
                        voiceTestCompleted = true
                        showCallCompleteButton = true
                        uiCallback.updateVoiceTesting(false)
                        uiCallback.updateVoiceDialog(false)
                        uiCallback.updateVoiceTestCompleted(true)
                        uiCallback.updateCallCompleteButton(true)
                    }
                    CallType.MANUAL_TRANSFER -> {
                        voiceTesting = false
                        showVoiceDialog = false
                        voiceTestCompleted = true
                        showCallCompleteButton = true
                        uiCallback.updateVoiceTesting(false)
                        uiCallback.updateVoiceDialog(false)
                        uiCallback.updateVoiceTestCompleted(true)
                        uiCallback.updateCallCompleteButton(true)
                    }
                    CallType.UPI123 -> {
                        Log.w(TAG, "UPI123 call ended in main initiateCall - this should not happen")
                    }
                    CallType.USSD -> {
                        showCallCompleteButton = true
                        uiCallback.updateCallCompleteButton(true)
                        emitTestStates()
                    }
                }
                currentTestingType = null
                emitTestStates()
            }
        )
    }


    private fun cancelUssdTimeout() {
        ussdTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        ussdTimeoutRunnable = null
        emitTestStates()
    }

    private fun cancelUpi123ConfigDelay() {
        upi123ConfigDelayRunnable?.let { mainHandler.removeCallbacks(it) }
        upi123ConfigDelayRunnable = null
        emitTestStates()
    }

    private fun startUssdTimeout() {
        Log.d(TAG, "Starting 25-second USSD timeout timer")
        cancelUssdTimeout()
        ussdTimeoutRunnable = Runnable {
            ussdTimeoutRunnable = null
            Log.d(TAG, "USSD 25-second timeout reached - showing configuration dialog")
            if (ussdTesting && !ussdTestCompleted) {
                showUssdDialog = false
                showUssdConfigurationOptions = true
                uiCallback.updateUssdDialog(false)
                uiCallback.updateUssdConfigurationOptions(true)
                emitTestStates()
                Log.d(TAG, "Configuration dialog shown after 25 seconds - showUssdDialog: $showUssdDialog, showUssdConfigurationOptions: $showUssdConfigurationOptions")
            } else {
                Log.d(TAG, "USSD timeout reached but conditions not met - ussdTesting: $ussdTesting, ussdTestCompleted: $ussdTestCompleted")
            }
        }
        mainHandler.postDelayed(ussdTimeoutRunnable!!, 25000)
    }

    fun dismissUssdDialog(fromDoesNotWork: Boolean = false) {
        if (showUssdConfigurationOptions) {
            handleUssdConfigurationConfirmation(false)
            return
        }
        cancelUssdTimeout()
        ussdTesting = false
        showUssdDialog = false
        showUssdConfigurationOptions = false
        uiCallback.updateUssdTesting(false)
        uiCallback.updateUssdDialog(false)
        uiCallback.updateUssdConfigurationOptions(false)
        emitTestStates()
        if (fromDoesNotWork) {
            SetupHelper.setUserReportedUssdNotWorking(context, true)
            uiCallback.showToast(context.getString(R.string.status_scan_to_pay_disabled))
        }
    }

    fun dismissUpi123Dialog() {
        if (showUpi123ConfigurationOptions) {
            handleUpi123ConfigurationConfirmation(false)
            return
        }
        cancelUpi123ConfigDelay()
        upi123Testing = false
        showUpi123Dialog = false
        showUpi123ConfigurationOptions = false
        uiCallback.updateUpi123Testing(false)
        uiCallback.updateUpi123Dialog(false)
        uiCallback.updateUpi123ConfigurationOptions(false)
        emitTestStates()
    }

    fun handleUssdConfigurationConfirmation(configured: Boolean) {
        Log.d(TAG, "USSD configuration confirmation: $configured")
        cancelUssdTimeout()

        showUssdConfigurationOptions = false
        showUssdDialog = false
        ussdTesting = false

        if (configured) {
            SetupHelper.setUserReportedUssdNotWorking(context, false)
            ussdTestCompleted = true
            showCallCompleteButton = true
            uiCallback.updateUssdTestCompleted(true)
            uiCallback.updateCallCompleteButton(true)
            saveTestResults(ussdTestCompleted, upi123TestCompleted)
            uiCallback.showToast(context.getString(R.string.status_ussd_setup_complete))
        } else {
            uiCallback.showToast(context.getString(R.string.status_ussd_setup_incomplete))
        }

        uiCallback.updateUssdConfigurationOptions(false)
        uiCallback.updateUssdDialog(false)
        uiCallback.updateUssdTesting(false)
        emitTestStates()
    }

    fun handleUpi123ConfigurationConfirmation(configured: Boolean) {
        Log.d(TAG, "UPI123 configuration confirmation: $configured")
        cancelUpi123ConfigDelay()

        showUpi123ConfigurationOptions = false
        showUpi123Dialog = false
        upi123Testing = false

        if (configured) {
            upi123TestCompleted = true
            showCallCompleteButton = true
            uiCallback.updateUpi123TestCompleted(true)
            uiCallback.updateCallCompleteButton(true)
            saveTestResults(ussdTestCompleted, upi123TestCompleted)
            uiCallback.showToast(context.getString(R.string.status_upi123_setup_complete))
        } else {
            uiCallback.showToast(context.getString(R.string.status_upi123_setup_incomplete))
        }

        uiCallback.updateUpi123ConfigurationOptions(false)
        uiCallback.updateUpi123Dialog(false)
        uiCallback.updateUpi123Testing(false)
        emitTestStates()
    }

    fun initiateUpi123Test() {
        Log.d(
            TAG,
            "initiateUpi123Test called - upi123TestCompleted: $upi123TestCompleted, upi123Testing: $upi123Testing"
        )
        if (!upi123TestCompleted && !upi123Testing) {
            Log.d(TAG, "Starting UPI123 test")
            upi123Testing = true
            showUpi123Dialog = true
            uiCallback.updateUpi123Testing(true)
            uiCallback.updateUpi123Dialog(true)
            emitTestStates()

            // Initiate UPI123 call - simple flow
            callManager.initiateCall(
                context = context,
                phoneNumber = AppConstants.DEFAULT_UPI_SERVICE_NUMBER,
                callType = CallType.UPI123,
                onCallEnded = { type ->
                    if (type == CallType.UPI123 && upi123Testing) {
                        Log.d(TAG, "UPI123 call ended - showing configuration dialog after 2 second delay")

                        // Stop testing state
                        upi123Testing = false
                        uiCallback.updateUpi123Testing(false)
                        emitTestStates()

                        cancelUpi123ConfigDelay()
                        upi123ConfigDelayRunnable = Runnable {
                            upi123ConfigDelayRunnable = null
                            if (!upi123TestCompleted) {
                                showUpi123Dialog = false
                                showUpi123ConfigurationOptions = true
                                uiCallback.updateUpi123Dialog(false)
                                uiCallback.updateUpi123ConfigurationOptions(true)
                                Log.d(TAG, "UPI123 configuration dialog shown after 2 second delay")
                            }
                        }
                        mainHandler.postDelayed(upi123ConfigDelayRunnable!!, 2000)
                    }
                }
            )
        } else {
            Log.d(TAG, "UPI123 test not started - already completed or testing")
        }
    }

    private fun saveTestResults(ussdCompleted: Boolean, upi123Completed: Boolean) {
        val results = TestResults(
            ussdEnabled = ussdCompleted,
            upi123Enabled = upi123Completed
        )
        testResultsManager.saveTestResults(results)
    }

    fun getTestResults(): TestResults? {
        return testResultsManager.getTestResults()
    }

    fun skipTests() {
        // Save current test results before continuing
        saveTestResults(ussdTestCompleted, upi123TestCompleted)
        emitTestStates()

        // Mark test configuration as completed even when skipped
        val sharedPreferences = context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("test_configuration_completed", true)
            .apply()

        uiCallback.navigateToMain()
    }

    fun continueToMain() {
        // Save test results before continuing
        saveTestResults(ussdTestCompleted, upi123TestCompleted)
        emitTestStates()

        // Mark test configuration as completed
        val sharedPreferences = context.getSharedPreferences("ProximityPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("test_configuration_completed", true)
            .apply()

        uiCallback.navigateToMain()
    }

    fun canContinue(): Boolean {
        val ussdWaived = !SetupHelper.isPrimarySimUssdCapable(context) ||
            SetupHelper.hasUserReportedUssdNotWorking(context)
        return (ussdTestCompleted || ussdWaived) || upi123TestCompleted
    }

    fun allTestsCompleted(): Boolean {
        val ussdWaived = !SetupHelper.isPrimarySimUssdCapable(context) ||
            SetupHelper.hasUserReportedUssdNotWorking(context)
        return (ussdTestCompleted || ussdWaived) && upi123TestCompleted
    }

    fun getTestStates(): TestStates {
        return _testStates.value
    }

    data class TestStates(
        val ussdTesting: Boolean,
        val showUssdDialog: Boolean,
        val ussdTestCompleted: Boolean,
        val upi123Testing: Boolean,
        val upi123TestCompleted: Boolean,
        val voiceTesting: Boolean,
        val showVoiceDialog: Boolean,
        val voiceTestCompleted: Boolean,
        val showCallCompleteButton: Boolean,
        val ussdProgressMessage: String,
        val showUssdConfigurationOptions: Boolean,
        val showUpi123Dialog: Boolean,
        val showUpi123ConfigurationOptions: Boolean
    )
}
