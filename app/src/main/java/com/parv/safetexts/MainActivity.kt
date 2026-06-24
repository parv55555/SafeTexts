package com.parv.safetexts

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.app.role.RoleManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parv.safetexts.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val sharedPref = getSharedPreferences("safetexts_prefs", MODE_PRIVATE)
        val hasAccepted = sharedPref.getBoolean("has_accepted_tc", false)

        setContent {
            SafeTextsTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = if (hasAccepted) "home" else "welcome") {
                    composable("welcome") { WelcomeScreen(navController, sharedPref) }
                    composable("home") { HomeScreen(navController) }
                    composable("permissions") { PermissionsScreen(navController) }
                    composable("terms") { TermsScreen(navController) }
                    composable("privacy") { PrivacyScreen(navController) }
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    
    // State for permissions
    var isNotificationEnabled by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isContactsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isOverlayPermissionGranted by remember {
        mutableStateOf(Settings.
        canDrawOverlays(context))
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isBatteryOptimized by remember { 
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) 
    }
    
    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
    var isCallScreeningRoleHeld by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else true)
    }

    var isPhoneStatePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launchers
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isContactsPermissionGranted = isGranted
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isPhoneStatePermissionGranted = isGranted
    }

    val callScreeningRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isCallScreeningRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }
    }

    // A simple way to refresh when the activity resumes
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationEnabled = isNotificationServiceEnabled(context)
                isContactsPermissionGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                isPhoneStatePermissionGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
                isOverlayPermissionGranted = Settings.canDrawOverlays(context)
                isBatteryOptimized = pm.isIgnoringBatteryOptimizations(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isCallScreeningRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Text(
                "Protection Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PermissionRow(
                        title = "Notification Access",
                        description = "Required to scan WhatsApp & SMS notifications for scams.",
                        isGranted = isNotificationEnabled,
                        onToggle = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    PermissionRow(
                        title = "Contacts Access",
                        description = "Used to identify messages from unknown numbers.",
                        isGranted = isContactsPermissionGranted,
                        onToggle = {
                            if (!isContactsPermissionGranted) {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    PermissionRow(
                        title = "Overlay Permission",
                        description = "Required to show scam warnings over other apps.",
                        isGranted = isOverlayPermissionGranted,
                        onToggle = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                "package:${context.packageName}".toUri()
                            )
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    PermissionRow(
                        title = "Battery Optimization",
                        description = "Prevent the system from killing the background scanner.",
                        isGranted = isBatteryOptimized,
                        onToggle = {
                            if (!isBatteryOptimized) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    PermissionRow(
                        title = "Phone State Access",
                        description = "Required to detect if you are currently on a call when an OTP arrives.",
                        isGranted = isPhoneStatePermissionGranted,
                        onToggle = {
                            if (!isPhoneStatePermissionGranted) {
                                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        PermissionRow(
                            title = "Call Screening App",
                            description = "Required to check if incoming calls are from unknown numbers during OTP verification.",
                            isGranted = isCallScreeningRoleHeld,
                            onToggle = {
                                if (!isCallScreeningRoleHeld) {
                                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                    callScreeningRoleLauncher.launch(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PermissionRow(title: String, description: String, isGranted: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val greenColor = if (isSystemInDarkTheme()) SuccessGreenDark else SuccessGreen
        Switch(
            checked = isGranted,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = greenColor
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val messages by db.messageDao().getAllMessages().collectAsStateWithLifecycle(initialValue = emptyList())
    val pm = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    var isNotificationEnabled by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    var isContactsPermissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    var isPhoneStatePermissionGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) }
    var isOverlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isBatteryOptimized by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    val roleManager = remember { context.getSystemService(Context.ROLE_SERVICE) as RoleManager }
    var isCallScreeningRoleHeld by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) else true)
    }

    val isAllGranted = isNotificationEnabled && isContactsPermissionGranted && isOverlayPermissionGranted && isBatteryOptimized && isPhoneStatePermissionGranted && isCallScreeningRoleHeld

    var searchQuery by remember { mutableStateOf("") }
    val filteredMessages = remember(searchQuery, messages) {
        if (searchQuery.isBlank()) {
            messages
        } else {
            messages.filter {
                it.sender.contains(searchQuery, ignoreCase = true) ||
                it.content.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationEnabled = isNotificationServiceEnabled(context)
                isContactsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                isPhoneStatePermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                isOverlayPermissionGranted = Settings.canDrawOverlays(context)
                isBatteryOptimized = pm.isIgnoringBatteryOptimizations(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isCallScreeningRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {}
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp)
        ) {
            // Header
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SafeTexts", fontWeight = FontWeight.Bold, fontSize = 30.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAllGranted && messages.isNotEmpty()) {
                            val scope = rememberCoroutineScope()
                            IconButton(onClick = { scope.launch { db.messageDao().deleteAll() } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete All")
                            }
                        }
                        IconButton(onClick = { navController.navigate("permissions") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                }
                Text(
                    "AI based scam message detector", 
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (isAllGranted && (messages.isNotEmpty() || searchQuery.isNotEmpty())) {
                    Spacer(modifier = Modifier.height(16.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (searchQuery.isEmpty()) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        decorationBox = { innerTextField ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Search",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            if (!isAllGranted) {
                // Blocked State
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Protection Disabled",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Protection Disabled",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "All permissions are required for protection.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { navController.navigate("permissions") },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Activate Protection", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Messages List
                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No messages scanned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Unknown messages will appear here.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else if (filteredMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredMessages) { message ->
                            val scope = rememberCoroutineScope()
                            MessageCard(
                                message = message,
                                onDelete = {
                                    scope.launch { db.messageDao().deleteMessage(message) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageCard(message: MessageEntity, onDelete: () -> Unit) {
    val statusColor = when {
        message.analysisResult.contains("Scam") -> ErrorRed
        message.analysisResult == "Safe" -> SuccessGreen
        else -> MaterialTheme.colorScheme.outline
    }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(message.timestamp) { dateFormat.format(Date(message.timestamp)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message.sender, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message.analysisResult.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    message.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms & Conditions") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Text("Terms and Conditions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Welcome to SafeTexts. By using our app, you agree to the following terms:\n\n" +
                "1. Acceptance of Terms\nBy accessing and using this application, you accept and agree to be bound by the terms and provision of this agreement.\n\n" +
                "2. Description of Service\nSafeTexts provides local on-device scanning of incoming notifications to detect potential scams using artificial intelligence.\n\n" +
                "3. Privacy & Data\nAll message scanning is performed locally on your device. We do not transmit your private messages to any external servers.\n\n" +
                "4. Disclaimer of Warranties\nThe app is provided \"as is\". We do not guarantee that all scams will be detected, nor do we take responsibility for false positives.\n\n" +
                "5. Limitation of Liability\nSafeTexts and its developers shall not be liable for any direct, indirect, incidental, or consequential damages resulting from the use or inability to use the service.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(navController: NavController) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Your privacy is our primary concern.\n\n" +
                "On-Device Processing:\n" +
                "SafeTexts was built with a strict privacy-first architecture. All machine learning inference and message analysis happens locally on your phone using PyTorch Mobile. We use our highly trained A0908i model for this purpose. Your messages never leave your device.\n\n" +
                "Permissions:\n" +
                "- Notification Access: Required strictly to read incoming messages for analysis.\n" +
                "- Contacts Access: Used locally to determine if a sender is known to you.\n" +
                "- Overlay Permission: Used to display the scam warning immediately over other apps.\n\n" +
                "Data Retention:\n" +
                "Scanned messages are stored in a local encrypted database on your device and are automatically deleted after 12 days to conserve space and maintain privacy.\n\n" +
                "Changes to Privacy Policy:\n" +
                "We reserve the right to modify this privacy policy at any time, so please review it frequently.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp
            )
        }
    }
}

fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}

@Composable
fun WelcomeScreen(navController: NavController, sharedPref: SharedPreferences) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security, 
                contentDescription = "Security Icon", 
                modifier = Modifier.size(80.dp), 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("SafeTexts", fontWeight = FontWeight.Bold, fontSize = 36.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Welcome to SafeTexts! Our on-device AI scans incoming notifications to detect scams, protecting you entirely locally. Your messages never leave your phone.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Terms & Conditions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { navController.navigate("terms") }
                )
                Text(
                    text = "  |  ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { navController.navigate("privacy") }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = {
                    sharedPref.edit { putBoolean("has_accepted_tc", true) }
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("I Accept T&C", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
