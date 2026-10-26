@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)

package com.debaxyz.selfattendance

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.debaxyz.selfattendance.data.model.fmtDate
import com.debaxyz.selfattendance.platform.LocalPlatformServices
import com.debaxyz.selfattendance.platform.PlatformBackHandler
import com.debaxyz.selfattendance.platform.PlatformServices
import com.debaxyz.selfattendance.platform.SystemBarsEffect
import com.debaxyz.selfattendance.platform.platformColorScheme
import com.debaxyz.selfattendance.platform.rememberFileActions
import com.debaxyz.selfattendance.ui.*
import com.debaxyz.selfattendance.ui.auth.AuthScreen
import com.debaxyz.selfattendance.ui.components.DeleteAccountDialog
import com.debaxyz.selfattendance.ui.components.UserProfileDialog
import kotlinx.coroutines.flow.collectLatest

/* ============================================================
 *  THEME (Material 3 Expressive + dynamic color)
 * ============================================================ */

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = platformColorScheme(dark)
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

/* ============================================================
 *  ROOT APP & AUTH GATE
 * ============================================================ */

@Composable
fun App(services: PlatformServices) {
    CompositionLocalProvider(LocalPlatformServices provides services) {
        AttendanceApp(services)
    }
}

@Composable
fun AttendanceApp(services: PlatformServices) {
    val vm: AttendanceViewModel = viewModel(factory = viewModelFactory {
        initializer { AttendanceViewModel(services) }
    })
    val files = rememberFileActions(onToast = vm::showToast, onRestoreText = vm::restore)
    val sysDark = isSystemInDarkTheme()
    val dark = vm.themeMode?.let { it == "dark" } ?: sysDark

    AppTheme(dark) {
        SystemBarsEffect(dark)

        // If not authenticated, render Auth Gate
        if (vm.currentUser == null) {
            AuthScreen(
                onAuthSuccess = {
                    // currentUser state is automatically updated via FirebaseAuth AuthStateListener in ViewModel
                }
            )
        } else {
            // Authenticated main application experience
            val snack = remember { SnackbarHostState() }
            LaunchedEffect(Unit) {
                vm.toasts.collectLatest { snack.showSnackbar(it) }
            }
            PlatformBackHandler(enabled = vm.editingKey != null || vm.showReset || vm.showProfileDialog) {
                vm.editingKey = null
                vm.showReset = false
                vm.showProfileDialog = false
            }

            val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    key(dark) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        "Self Attendance",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        fmtDate(vm.today, "EEEE, d MMMM yyyy"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                // Cloud Sync indicator & Account Profile Button
                                FilledTonalIconButton(
                                    onClick = { vm.showProfileDialog = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    val user = vm.currentUser
                                    val initial = (user?.displayName?.firstOrNull() ?: user?.email?.firstOrNull() ?: 'U').uppercaseChar()
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            initial.toString(),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(Modifier.width(6.dp))

                                FilledTonalIconButton(
                                    onClick = { vm.toggleTheme(sysDark) },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Switch theme",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            scrollBehavior = scrollBehavior
                        )
                    }
                },
                bottomBar = {
                    NavigationBar {
                        listOf(
                            Triple("home", Icons.Rounded.Home, "Home"),
                            Triple("recent", Icons.Rounded.History, "Recent days"),
                            Triple("settings", Icons.Rounded.Settings, "Settings")
                        ).forEach { (t, icon, label) ->
                            NavigationBarItem(
                                selected = vm.tab == t,
                                onClick = { vm.tab = t },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snack) }
            ) { inner ->
                key(vm.tab) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (vm.tab) {
                            "home" -> HomeView(vm, files)
                            "recent" -> RecentView(vm, files)
                            else -> SettingsView(vm, files)
                        }
                    }
                }
            }

            vm.editingKey?.let { EditSheet(vm, it) }
            if (vm.showReset) ResetDialog(vm)
            if (vm.showProfileDialog) {
                UserProfileDialog(
                    isSyncing = vm.isSyncing,
                    recordCount = vm.data.size,
                    onDismiss = { vm.showProfileDialog = false },
                    onSignedOut = {
                        vm.showProfileDialog = false
                    },
                    onSignOut = {
                        vm.signOut {
                            vm.showProfileDialog = false
                        }
                    },
                    onDeleteAccount = {
                        vm.deleteAccountError = null
                        vm.showProfileDialog = false
                        vm.showDeleteAccount = true
                    }
                )
            }
            if (vm.showDeleteAccount) {
                DeleteAccountDialog(
                    usesPassword = vm.currentUser?.usesGoogle == false,
                    isDeleting = vm.isDeletingAccount,
                    error = vm.deleteAccountError,
                    onConfirm = { pw -> vm.deleteAccount(pw) },
                    onDismiss = {
                        if (!vm.isDeletingAccount) {
                            vm.showDeleteAccount = false
                            vm.deleteAccountError = null
                        }
                    }
                )
            }
        }
    }
}
