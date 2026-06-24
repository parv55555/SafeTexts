package com.parv.safetexts

import android.content.Context
import android.content.SharedPreferences

object OtpManager {
    private const val PREFS_NAME = "otp_security_prefs"
    private const val KEY_LAST_OTP_TIME = "last_otp_time"
    private const val KEY_LAST_UNKNOWN_CALL_TIME = "last_unknown_call_time"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLastOtpTime(context: Context, timestamp: Long = System.currentTimeMillis()) {
        getPrefs(context).edit().putLong(KEY_LAST_OTP_TIME, timestamp).apply()
    }

    fun getLastOtpTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_OTP_TIME, 0L)
    }

    fun saveLastUnknownCallTime(context: Context, timestamp: Long = System.currentTimeMillis()) {
        getPrefs(context).edit().putLong(KEY_LAST_UNKNOWN_CALL_TIME, timestamp).apply()
    }

    fun getLastUnknownCallTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UNKNOWN_CALL_TIME, 0L)
    }
}
