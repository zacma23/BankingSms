package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var isSigningIn by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // SMS Permission Check
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasSmsPermission = perms[Manifest.permission.RECEIVE_SMS] == true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Synchronization",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Google Authentication & Cloud Backup Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Account",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (currentUser != null) currentUser!!.displayName ?: "Signed In User" else "Google Account & Backup",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (currentUser != null) currentUser!!.email ?: "" else "Sign in to backup transactions to Cloud Firestore",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (currentUser == null) {
                        Button(
                            onClick = {
                                isSigningIn = true
                                val clientId = try {
                                    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                                    if (resId != 0) context.getString(resId) else ""
                                } catch (e: Exception) {
                                    ""
                                }

                                if (clientId.isBlank()) {
                                    Toast.makeText(context, "Google Client ID not configured in google-services.json", Toast.LENGTH_SHORT).show()
                                    isSigningIn = false
                                    return@Button
                                }

                                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                                coroutineScope.launch {
                                    try {
                                        val result = credentialManager.getCredential(context as Activity, request)
                                        val cred = result.credential
                                        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                            val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                                            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                                            Firebase.auth.signInWithCredential(authCredential).await()
                                            Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: GetCredentialCancellationException) {
                                        Log.w("Auth", "Cancelled: ${e.message}")
                                    } catch (e: Exception) {
                                        Log.e("Auth", "Sign-in error", e)
                                        Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSigningIn = false
                                    }
                                }
                            },
                            enabled = !isSigningIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_signin_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Sign in with Google")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudDone, contentDescription = "Synced", tint = IncomeGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Sync Active", color = IncomeGreen, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                            }

                            OutlinedButton(
                                onClick = {
                                    Firebase.auth.signOut()
                                    coroutineScope.launch {
                                        try {
                                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                        } catch (e: Exception) {
                                            Log.e("Auth", "Clear state error", e)
                                        }
                                    }
                                }
                            ) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }
        }

        // 2. Android SMS Permission Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (hasSmsPermission) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sms,
                                contentDescription = "SMS",
                                tint = if (hasSmsPermission) IncomeGreen else ExpenseRed
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "SMS Reception Permission",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (hasSmsPermission) "Active • Automatic bank detection enabled" else "Required to auto-detect incoming bank SMS",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasSmsPermission) IncomeGreen else ExpenseRed
                            )
                        }
                    }

                    if (!hasSmsPermission) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_sms_permission_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant SMS & Notification Permissions")
                        }
                    }
                }
            }
        }

        // 3. Data Management
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Management",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All data is securely stored in local Room/SQLite database with unique fingerprint hashing to prevent duplicate entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear", tint = ExpenseRed)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear All Transactions", color = ExpenseRed)
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Clear All Transactions?") },
            text = { Text("This will permanently remove all stored transactions from the local database.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTransactions()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
