package com.debaxyz.selfattendance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private const val CONFIRM_WORD = "DELETE"

/**
 * Permanent account deletion confirmation.
 * [usesPassword] = true for email/password accounts (asks for the password);
 * Google accounts are re-verified through the Google account sheet instead.
 */
@Composable
fun DeleteAccountDialog(
    usesPassword: Boolean,
    isDeleting: Boolean,
    error: String?,
    onConfirm: (password: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var confirmText by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val canDelete = !isDeleting &&
        confirmText.trim() == CONFIRM_WORD &&
        (!usesPassword || password.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isDeleting, dismissOnClickOutside = !isDeleting),
        icon = { Icon(Icons.Rounded.DeleteForever, null, tint = cs.error) },
        title = { Text("Delete account?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This permanently deletes your account and all cloud data: attendance records, " +
                        "shift settings and wage rates. Data on this device is erased too. " +
                        "This cannot be undone. Consider taking a Backup from Settings first.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!usesPassword) {
                    Text(
                        "You will be asked to confirm with your Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = confirmText,
                    onValueChange = { confirmText = it },
                    label = { Text("Type $CONFIRM_WORD to confirm") },
                    singleLine = true,
                    enabled = !isDeleting,
                    modifier = Modifier.fillMaxWidth()
                )
                if (usesPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Account password") },
                        singleLine = true,
                        enabled = !isDeleting,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(if (usesPassword) password else null) },
                enabled = canDelete,
                colors = ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError)
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onError)
                    Spacer(Modifier.width(8.dp))
                    Text("Deleting...")
                } else {
                    Text("Delete forever")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Cancel") }
        }
    )
}
