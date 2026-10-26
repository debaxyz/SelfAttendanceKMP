package com.debaxyz.selfattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.debaxyz.selfattendance.platform.LocalPlatformServices
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.debaxyz.selfattendance.ui.auth.signOut

@Composable
fun UserProfileDialog(
    isSyncing: Boolean,
    recordCount: Int,
    onDismiss: () -> Unit,
    onSignedOut: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null
) {
    val services = LocalPlatformServices.current
    val user = services.auth.currentUser
    val cloud = services.cloudEnabled
    var showConfirmSignOut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = (user?.displayName?.firstOrNull()
                            ?: user?.email?.firstOrNull() ?: 'U').uppercaseChar()
                        Text(
                            initial.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column {
                    Text(
                        user?.displayName ?: "Google User",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            if (isSyncing) Icons.Rounded.Sync else Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (isSyncing) MaterialTheme.colorScheme.primary else Color(0xFF16A34A)
                        )
                        Column {
                            Text(
                                if (isSyncing) "Syncing with cloud..." else "Cloud Synced",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$recordCount attendance records stored ${if (cloud) "in Firestore" else "on this device"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    if (cloud) "You are securely signed in with Google. All shifts, wage rates, and records are synced across your devices."
                    else "Local mode: your data stays on this device. Cloud sync is available in the Android, iOS and web (JS) builds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (cloud && onDeleteAccount != null) {
                    TextButton(
                        onClick = onDeleteAccount,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete account & data")
                    }
                }
            }
        },
        confirmButton = {
            if (cloud) TextButton(
                onClick = { showConfirmSignOut = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sign out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )

    if (showConfirmSignOut) {
        AlertDialog(
            onDismissRequest = { showConfirmSignOut = false },
            title = { Text("Sign out of Google account?") },
            text = { Text("Your attendance data remains securely saved in the cloud and will be restored when you sign in again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSignOut = false
                        onDismiss()
                        if (onSignOut != null) {
                            onSignOut()
                        } else {
                            signOut(services.google, services.auth, onSignedOut)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSignOut = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
