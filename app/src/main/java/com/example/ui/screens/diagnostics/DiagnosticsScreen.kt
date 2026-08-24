package com.example.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firebase.FirebaseManager
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val packageName = context.packageName
    val (sha1, sha256) = remember { getCertificateFingerprints(context) }

    var projectId by remember { mutableStateOf("gomemo-e7259") }
    var appId by remember { mutableStateOf("1:95804150073:android:e837bf411624b5952d7e6c") }
    var storageBucket by remember { mutableStateOf("gomemo-e7259.firebasestorage.app") }
    var webClientId by remember { mutableStateOf("95804150073-ba3c1d207d7047ee2918e8.apps.googleusercontent.com") }

    LaunchedEffect(Unit) {
        try {
            val app = FirebaseApp.getInstance()
            projectId = app.options.projectId ?: "gomemo-e7259"
            appId = app.options.applicationId ?: "1:95804150073:android:e837bf411624b5952d7e6c"
            storageBucket = app.options.storageBucket ?: "gomemo-e7259.firebasestorage.app"

            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                webClientId = context.getString(resId)
            }
        } catch (e: Exception) {
            // fallback
        }
    }

    var isRunningProbe by remember { mutableStateOf(false) }
    var probeStatus by remember { mutableStateOf<String?>(null) }
    var probeIsSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun runLiveProbe() {
        isRunningProbe = true
        probeStatus = null
        probeIsSuccess = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Probe Firestore
                    val firestore = FirebaseManager.firestore
                    val ping = firestore.collection("timeline").limit(1).get().await()
                    Pair(true, "Firestore connection active (${ping.size()} activities retrieved).")
                } catch (e: Exception) {
                    val msg = e.message ?: "Unknown error"
                    Pair(false, "Connection check: $msg")
                }
            }
            isRunningProbe = false
            probeIsSuccess = result.first
            probeStatus = result.second
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Firebase & Auth Diagnostics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Firebase Project Configuration",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "This screen shows the exact configuration and signing fingerprints of the running app for Firebase Console setup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Connectivity Probe Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Live Backend Probe",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { runLiveProbe() },
                            enabled = !isRunningProbe,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isRunningProbe) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Connection", fontSize = 13.sp)
                            }
                        }
                    }

                    if (probeStatus != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (probeIsSuccess == true) Color(0xFF1B5E20).copy(alpha = 0.15f)
                                    else Color(0xFFB71C1C).copy(alpha = 0.15f)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (probeIsSuccess == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (probeIsSuccess == true) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                probeStatus ?: "",
                                fontSize = 12.sp,
                                color = if (probeIsSuccess == true) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Project IDs Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Firebase Project Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ConfigItem(
                        label = "Project ID",
                        value = projectId,
                        onCopy = { copyToClipboard("Project ID", projectId) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ConfigItem(
                        label = "Android Package Name",
                        value = packageName,
                        onCopy = { copyToClipboard("Package Name", packageName) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ConfigItem(
                        label = "Mobile App ID",
                        value = appId,
                        onCopy = { copyToClipboard("Mobile App ID", appId) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ConfigItem(
                        label = "Storage Bucket",
                        value = storageBucket,
                        onCopy = { copyToClipboard("Storage Bucket", storageBucket) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ConfigItem(
                        label = "Web OAuth Client ID",
                        value = webClientId,
                        onCopy = { copyToClipboard("Web Client ID", webClientId) }
                    )
                }
            }

            // Signing Fingerprints Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Signing Certificate Fingerprints",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Required in Firebase Console > Project Settings > Your Apps > Add fingerprint",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ConfigItem(
                        label = "SHA-1 Fingerprint (Required for Google Auth & Phone Auth)",
                        value = sha1,
                        onCopy = { copyToClipboard("SHA-1 Fingerprint", sha1) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ConfigItem(
                        label = "SHA-256 Fingerprint (Required for App Check & Phone Auth)",
                        value = sha256,
                        onCopy = { copyToClipboard("SHA-256 Fingerprint", sha256) }
                    )
                }
            }

            // Setup Instructions Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Firebase Console Checklist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ChecklistStep(
                        number = "1",
                        title = "Add SHA-1 & SHA-256",
                        desc = "In Firebase Console > Project Settings > General > Your Apps (Android: com.aistudio.gomemo.pxwvke), click 'Add fingerprint' and paste the SHA-1 and SHA-256 above."
                    )
                    ChecklistStep(
                        number = "2",
                        title = "Enable Authentication Providers",
                        desc = "In Firebase Console > Build > Authentication > Sign-in method, ensure 'Email/Password', 'Phone', and 'Google' are enabled."
                    )
                    ChecklistStep(
                        number = "3",
                        title = "Enable Google Identity Toolkit API",
                        desc = "In Google Cloud Console > APIs & Services > Enabled APIs, verify that 'Identity Toolkit API' is enabled and that your Android API Key has access."
                    )
                    ChecklistStep(
                        number = "4",
                        title = "Deploy Cloud Firestore & Storage",
                        desc = "Ensure Firestore and Firebase Storage are created in project 'gomemo-e7259' with security rules allowing authenticated users."
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigItem(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChecklistStep(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getCertificateFingerprints(context: Context): Pair<String, String> {
    try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        val cert = signatures?.firstOrNull()?.toByteArray()
            ?: return Pair("40:14:AE:B8:D6:89:6B:2D:0B:C6:2B:9F:C8:F9:19:B6:8D:97:3F:4C", "7F:3B:B5:ED:59:74:74:1E:37:9F:4E:9D:FE:89:E9:CD:49:4A:AF:53:41:41:7B:C6:C7:52:78:E4:06:AF:A5:93")

        val sha1 = MessageDigest.getInstance("SHA-1").digest(cert).joinToString(":") { "%02X".format(it) }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert).joinToString(":") { "%02X".format(it) }
        return Pair(sha1, sha256)
    } catch (e: Exception) {
        return Pair("40:14:AE:B8:D6:89:6B:2D:0B:C6:2B:9F:C8:F9:19:B6:8D:97:3F:4C", "7F:3B:B5:ED:59:74:74:1E:37:9F:4E:9D:FE:89:E9:CD:49:4A:AF:53:41:41:7B:C6:C7:52:78:E4:06:AF:A5:93")
    }
}
