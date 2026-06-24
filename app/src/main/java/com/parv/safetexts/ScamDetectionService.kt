package com.parv.safetexts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.withContext

class ScamDetectionService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private lateinit var database: AppDatabase
    private var vocab: Map<String, Int>? = null

    private var appInstallReceiver: AppInstallReceiver? = null

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        loadVocab()
        createNotificationChannel()
        cleanupOldMessages()

        val filter = android.content.IntentFilter(android.content.Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        appInstallReceiver = AppInstallReceiver()
        registerReceiver(appInstallReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        appInstallReceiver?.let {
            unregisterReceiver(it)
        }
    }

    private fun cleanupOldMessages() {
        serviceScope.launch {
            try {
                val twelveDaysInMillis = 12L * 24 * 60 * 60 * 1000
                val threshold = System.currentTimeMillis() - twelveDaysInMillis
                database.messageDao().deleteMessagesOlderThan(threshold)
                Log.d("ScamDetection", "Cleaned up messages older than 12 days")
            } catch (e: Exception) {
                Log.e("ScamDetection", "Error cleaning up messages: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Scam Alerts"
            val descriptionText = "Notifications for detected scam messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("SCAM_ALERTS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun loadVocab() {
        serviceScope.launch {
            try {
                // Load Vocabulary
                vocab = applicationContext.assets.open("vocab.txt").bufferedReader().useLines { lines ->
                    lines.withIndex().associate { it.value to it.index }
                }
                Log.d("ScamDetection", "Vocab loaded successfully")
            } catch (e: Exception) {
                Log.e("ScamDetection", "Error loading vocab: ${e.message}")
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        cleanupOldMessages()
        
        val packageName = sbn.packageName
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(applicationContext)
        val isWhatsApp = packageName == "com.whatsapp"
        val isSms = packageName == defaultSmsPackage || 
                    packageName.contains("messaging") || 
                    packageName.contains("sms")
        
        if (!isWhatsApp && !isSms) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "Unknown Sender"
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (text.isEmpty()) return
        if (isSystemNotification(title, text)) return

        // Check for OTPs before filtering out business senders
        val hasOtpKeyword = text.contains(Regex("(?i)\\b(otp|code|verification|password)\\b"))
        val hasNumberCode = text.contains(Regex("\\b\\d{4,8}\\b"))
        
        // Ensure it's a high-value OTP (Bank, Aadhar, PAN, Govt) to ignore delivery OTPs
        val highValueRegex = Regex("(?i)\\b(bank|aadhar|aadhaar|uidai|pan|gov|government|kyc|tax|sbi|hdfc|icici|axis|pnb|bob)\\b")
        val isHighValue = text.contains(highValueRegex) || title.contains(highValueRegex)
        
        val isOtp = hasOtpKeyword && hasNumberCode && isHighValue
        
        if (isOtp) {
            Log.d("ScamDetection", "OTP detected from $title")
            OtpManager.saveLastOtpTime(applicationContext)

            val tm = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            if (tm.callState == android.telephony.TelephonyManager.CALL_STATE_OFFHOOK) {
                val lastUnknownCall = OtpManager.getLastUnknownCallTime(applicationContext)
                // If an unknown call was received within the last 2 hours, assume this active call might be the scammer
                if (System.currentTimeMillis() - lastUnknownCall <= 2L * 60 * 60 * 1000) {
                    serviceScope.launch {
                        withContext(Dispatchers.Main) {
                            OverlayUtils.showOtpWarningOverlay(applicationContext)
                        }
                    }
                }
            }
        }

        // Ignore SMS from business/alphanumeric senders (only allow purely numeric/phone-number senders) for normal scam processing
        if (isSms) {
            val isPhoneNumber = title.matches(Regex("^[\\d\\s\\+\\-\\(\\)]+\$"))
            if (!isPhoneNumber) {
                Log.d("ScamDetection", "Ignoring SMS from non-numeric sender: $title")
                return
            }
        }

        serviceScope.launch {
            if (!ContactUtils.isContactExists(applicationContext, title)) {
                // De-duplication check: if this exact message already exists, check its analysis result
                val existingMessage = database.messageDao().getMessage(title, text)
                if (existingMessage != null) {
                    Log.d("ScamDetection", "Duplicate message found: $title - $text")
                    if (existingMessage.analysisResult == "Scam") {
                        withContext(Dispatchers.Main) {
                            showScamWarningOverlay(existingMessage)
                        }
                    }
                    return@launch
                }

                // Deterministic rule: .apk messages are automatically scams
                if (text.lowercase(Locale.ROOT).contains(".apk")) {
                    Log.d("ScamDetection", "Deterministic scam found (.apk): $title - $text")
                    val scamMessage = MessageEntity(
                        sender = title,
                        content = text,
                        appName = if (isWhatsApp) "WhatsApp" else "SMS",
                        analysisResult = "Scam",
                        scamProbability = 1.0f
                    )
                    database.messageDao().insertMessage(scamMessage)
                    withContext(Dispatchers.Main) {
                        showScamWarningOverlay(scamMessage)
                    }
                    return@launch
                }

                val message = MessageEntity(
                    sender = title,
                    content = text,
                    appName = if (isWhatsApp) "WhatsApp" else "SMS"
                )
                val id = database.messageDao().insertMessage(message)
                
                // Analyze the message using the returned row ID
                analyzeMessage(message.copy(id = id.toInt()))
            }
        }
    }

    private fun analyzeMessage(message: MessageEntity) {
        val currentVocab = vocab
        
        if (currentVocab == null) {
            Log.e("ScamDetection", "Vocab not ready for analysis")
            serviceScope.launch {
                database.messageDao().updateMessage(message.copy(analysisResult = "Vocab Error"))
            }
            return
        }

        serviceScope.launch {
            var module: Module? = null
            try {
                // Dynamically load the model
                val modelPath = assetFilePath(this@ScamDetectionService, "A0908i.ptl")
                module = LiteModuleLoader.load(modelPath)

                // 1. Pre-processing / Tokenization
                val tokens = tokenize(message.content, currentVocab)
                
                // Prepare input tensors (Batch Size 1, Sequence Length matching tokens size)
                val inputTensor = Tensor.fromBlob(tokens.toLongArray(), longArrayOf(1, tokens.size.toLong()))
                
                // Prepare attention mask (1 for real tokens, 0 for padding)
                val attentionArray = tokens.map { if (it == 0L) 0L else 1L }.toLongArray()
                val attentionTensor = Tensor.fromBlob(attentionArray, longArrayOf(1, attentionArray.size.toLong()))

                // 2. Inference
                // Pass both input_ids and attention_mask to the model
                val outputIValue = module.forward(IValue.from(inputTensor), IValue.from(attentionTensor))
                val outputTensor = outputIValue.toTensor()
                val scores = outputTensor.dataAsFloatArray
                
                // Logic based on typical binary classification [safe_prob, scam_prob]
                // If model returns a single value, we treat it as probability.
                val scamProbability = if (scores.size > 1) scores[1] else scores[0] 
                val result = if (scamProbability > 0.5f) "Scam" else "Safe"

                // 3. Update Database with the result
                val analyzedMessage = message.copy(
                    analysisResult = result,
                    scamProbability = scamProbability
                )
                database.messageDao().updateMessage(analyzedMessage)
                
                if (result == "Scam") {
                    withContext(Dispatchers.Main) {
                        showScamWarningOverlay(analyzedMessage)
                    }
                }
                
                Log.d("ScamDetection", "Result for '${message.content}': $result ($scamProbability)")
            } catch (e: Throwable) {
                Log.e("ScamDetection", "Inference failed: ${e.message}")
                val errorMsg = e.message?.take(30) ?: e.javaClass.simpleName
                database.messageDao().updateMessage(message.copy(analysisResult = "Failed: $errorMsg"))
            } finally {
                // Unload the model to free memory
                module?.destroy()
            }
        }
    }

    private fun tokenize(text: String, vocab: Map<String, Int>): List<Long> {
        // Simple whitespace tokenizer to match basic NLP models
        val words = text.lowercase(Locale.ROOT).split(Regex("\\s+"))
        val maxLen = 50 // Standard length, adjust if your model expects more/less
        
        val tokens = mutableListOf<Long>(101L) // [CLS]
        tokens.addAll(words.map { word -> 
            vocab[word]?.toLong() ?: 100L // Default to 100 for <UNK> instead of 0
        })
        tokens.add(102L) // [SEP]

        // Padding/Trimming to ensure consistent input size
        if (tokens.size > maxLen) {
            val truncated = tokens.subList(0, maxLen - 1).toMutableList()
            truncated.add(102L) // Ensure [SEP] is at the end
            return truncated
        } else {
            while (tokens.size < maxLen) {
                tokens.add(0L) // Pad with 0
            }
        }
        return tokens
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            }
        }
        return file.absolutePath
    }

    private fun showScamWarningOverlay(message: MessageEntity) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("ScamDetection", "Overlay permission not granted. Showing fallback notification.")
            showScamWarningNotification(message)
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        layoutParams.y = 100 // Slightly offset from the very top

        // Container
        val overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FFF0F0")) // Light red background
                cornerRadius = 32f
                setStroke(4, Color.parseColor("#FF5252")) // Red border
            }
            background = bgDrawable
            setPadding(48, 48, 48, 48)
        }

        // Title
        val titleView = TextView(this).apply {
            text = "⚠️ SCAM WARNING"
            textSize = 20f
            setTextColor(Color.parseColor("#D32F2F")) // Dark red text
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        // Sender Info
        val senderView = TextView(this).apply {
            text = "From: ${message.sender} (${message.appName})"
            textSize = 16f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32) // Increased padding since snippet is removed
        }

        // Dismiss Button
        val dismissButton = Button(this).apply {
            text = "Dismiss"
            setTextColor(Color.WHITE)
            val btnDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#FF5252"))
                cornerRadius = 16f
            }
            background = btnDrawable
            setPadding(32, 16, 32, 16)
            setOnClickListener {
                windowManager.removeView(overlayView)
            }
        }

        // Add views to container
        overlayView.addView(titleView)
        overlayView.addView(senderView)
        overlayView.addView(dismissButton)

        // Add container to WindowManager
        try {
            windowManager.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e("ScamDetection", "Failed to add overlay view: ${e.message}")
        }
    }

    private fun isSystemNotification(title: String, text: String): Boolean {
        val lowerText = text.lowercase(Locale.ROOT).trim()
        val lowerTitle = title.lowercase(Locale.ROOT).trim()

        // Ignore summary group notifications which usually have "WhatsApp" as the title
        if (lowerTitle == "whatsapp") return true

        val systemPhrases = listOf(
            "checking for new messages",
            "missed voice call",
            "missed video call",
            "incoming voice call",
            "incoming video call",
            "whatsapp web is currently active",
            "backup in progress"
        )

        if (systemPhrases.any { lowerText.contains(it) }) return true

        // Ignore "1 new message", "2 new messages from 2 chats", etc.
        if (lowerText.matches(Regex("^\\d+\\s+new\\s+messages?.*"))) return true

        return false
    }

    private fun showScamWarningNotification(message: MessageEntity) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val snippet = if (message.content.length > 50) {
            message.content.substring(0, 50) + "..."
        } else {
            message.content
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "SCAM_ALERTS")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ SCAM WARNING: ${message.sender}")
            .setContentText("\"$snippet\"")
            .setStyle(Notification.BigTextStyle().bigText("From: ${message.sender} (${message.appName})\n\n${message.content}"))
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(message.id, notification)
    }
}
