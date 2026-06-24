package com.parv.safetexts

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

class OtpCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = getPhoneNumber(callDetails.handle)
        
        // Respond to the call immediately so we don't block anything
        val response = CallResponse.Builder().build()
        respondToCall(callDetails, response)

        if (phoneNumber == null) return

        if (!ContactUtils.isContactExists(applicationContext, phoneNumber)) {
            Log.d("OtpSecurity", "Incoming call from unknown number: $phoneNumber")
            OtpManager.saveLastUnknownCallTime(applicationContext)

            val lastOtpTime = OtpManager.getLastOtpTime(applicationContext)
            val sevenMinutesInMillis = 7L * 60 * 1000

            if (System.currentTimeMillis() - lastOtpTime <= sevenMinutesInMillis) {
                Log.d("OtpSecurity", "OTP received recently. Triggering overlay.")
                Handler(Looper.getMainLooper()).post {
                    OverlayUtils.showOtpWarningOverlay(applicationContext)
                }
            }
        }
    }

    private fun getPhoneNumber(handle: Uri?): String? {
        if (handle == null) return null
        val scheme = handle.scheme
        if (scheme != null && scheme == "tel") {
            return handle.schemeSpecificPart
        }
        return null
    }
}
