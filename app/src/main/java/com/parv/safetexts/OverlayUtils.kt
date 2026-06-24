package com.parv.safetexts

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object OverlayUtils {

    fun showOtpWarningOverlay(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("OtpSecurity", "Overlay permission not granted for OTP warning.")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP
        layoutParams.y = 100

        val overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF8E1")) // Light yellow/orange background
                cornerRadius = 32f
                setStroke(4, Color.parseColor("#FFA000")) // Orange border
            }
            background = bgDrawable
            setPadding(48, 48, 48, 48)
        }

        val titleView = TextView(context).apply {
            text = "⚠️ OTP Security Alert"
            textSize = 20f
            setTextColor(Color.parseColor("#F57C00")) // Dark orange text
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val messageView = TextView(context).apply {
            text = "You recently received an OTP. Do NOT share it with anyone on this call or message. Bank officials will never ask for your OTP."
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val dismissButton = Button(context).apply {
            text = "I Understand"
            setTextColor(Color.WHITE)
            val btnDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FFA000"))
                cornerRadius = 16f
            }
            background = btnDrawable
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e("OtpSecurity", "Error removing overlay", e)
                }
            }
        }

        overlayView.addView(titleView)
        overlayView.addView(messageView)
        overlayView.addView(dismissButton)

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e("OtpSecurity", "Failed to add overlay view: ${e.message}")
        }
    }

    fun showRatWarningOverlay(context: Context, isSevere: Boolean) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w("RatWatchdog", "Overlay permission not granted for RAT warning.")
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.CENTER
        layoutParams.y = 0

        val overlayView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bgDrawable = GradientDrawable().apply {
                setColor(if (isSevere) Color.parseColor("#FFEBEE") else Color.parseColor("#FFF3E0")) 
                cornerRadius = 32f
                setStroke(6, if (isSevere) Color.parseColor("#D50000") else Color.parseColor("#F57C00")) 
            }
            background = bgDrawable
            setPadding(64, 64, 64, 64)
        }

        val titleView = TextView(context).apply {
            text = if (isSevere) "🚨 DANGER: SCAM IN PROGRESS" else "⚠️ Remote Access Warning"
            textSize = 22f
            setTextColor(if (isSevere) Color.parseColor("#B71C1C") else Color.parseColor("#E65100"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        val messageView = TextView(context).apply {
            val msgText = if (isSevere) 
                "You just installed a screen-sharing app while on a call with an UNKNOWN number! They are trying to steal your banking details. DO NOT open the app and HANG UP IMMEDIATELY!"
            else 
                "You just installed a screen-sharing app. Scammers commonly trick people into downloading these apps to steal money. Be very careful!"
            text = msgText
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 48)
        }

        val dismissButton = Button(context).apply {
            text = if (isSevere) "I UNDERSTAND THE RISK" else "Got It"
            setTextColor(Color.WHITE)
            val btnDrawable = GradientDrawable().apply {
                setColor(if (isSevere) Color.parseColor("#D50000") else Color.parseColor("#F57C00"))
                cornerRadius = 16f
            }
            background = btnDrawable
            setPadding(32, 24, 32, 24)
            setOnClickListener {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e("RatWatchdog", "Error removing overlay", e)
                }
            }
        }

        overlayView.addView(titleView)
        overlayView.addView(messageView)
        overlayView.addView(dismissButton)

        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e("RatWatchdog", "Failed to add overlay view: ${e.message}")
        }
    }
}
