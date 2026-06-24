package com.parv.safetexts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class AppInstallReceiver : BroadcastReceiver() {

    // Comprehensive list of Remote Access / Screen Sharing apps commonly used in scams
    private val blacklistedApps = setOf(
        "com.anydesk.anydeskandroid",
        "com.teamviewer.quicksupport.market",
        "com.teamviewer.host.market",
        "com.carriez.rustdesk",
        "com.apowersoft.mirror",
        "com.microsoft.rdc.androidx",
        "com.google.chromeremotedesktop",
        "com.sand.airdroid",
        "com.realvnc.viewer.android"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val packageName = intent.data?.schemeSpecificPart
            Log.d("RatWatchdog", "App installed: $packageName")

            if (packageName != null && blacklistedApps.contains(packageName)) {
                Log.d("RatWatchdog", "DANGER: Blacklisted remote access app installed!")

                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val isCallActive = tm.callState == TelephonyManager.CALL_STATE_OFFHOOK

                val lastUnknownCall = OtpManager.getLastUnknownCallTime(context)
                // If an unknown call happened within the last 2 hours, we consider it "recent"
                val isRecentUnknownCall = (System.currentTimeMillis() - lastUnknownCall) <= 2L * 60 * 60 * 1000

                // Scenario A: Active call AND unknown caller recently
                val isSevere = isCallActive && isRecentUnknownCall

                // Show the warning overlay
                OverlayUtils.showRatWarningOverlay(context, isSevere)
            }
        }
    }
}
