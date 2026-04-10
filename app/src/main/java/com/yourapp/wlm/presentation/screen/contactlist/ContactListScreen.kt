package com.yourapp.wlm.presentation.screen.contactlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yourapp.wlm.domain.model.ContactGroup
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.repository.ConnectionState
import com.yourapp.wlm.presentation.common.AvatarImage
import com.yourapp.wlm.presentation.common.LoadingIndicator
import com.yourapp.wlm.presentation.common.StatusBadge
import com.yourapp.wlm.presentation.screen.contactlist.components.ContactItem
import com.yourapp.wlm.presentation.screen.contactlist.components.GroupHeader
import com.yourapp.wlm.presentation.screen.contactlist.components.StatusSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    viewModel: ContactListViewModel = hiltViewModel(),
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val logoutEvent by viewModel.logoutEvent.collectAsState()

    LaunchedEffect(logoutEvent) {
        if (logoutEvent) {
            onLogout()
        }
    }

    var addContactEmail by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.userDisplayName)
                        Text(
                            text = uiState.userPersonalMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        AvatarImage(
                            imageUrl = null,
                            contentDescription = "Profile",
                            size = 32
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Default.ExitToApp, "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showAddContactDialog) {
                Icon(Icons.Default.PersonAdd, "Add Contact")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ConnectionStatusBar(uiState.connectionState)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    StatusSelector(
                        currentStatus = uiState.userStatus,
                        onStatusSelected = viewModel::onChangeStatus
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.userPersonalMessage,
                        onValueChange = viewModel::onPersonalMessageChange,
                        label = { Text("Personal Message") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = viewModel::savePersonalMessage,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                uiState.groups.forEach { group ->
                    item(key = "header_${group.groupId}") {
                        GroupHeader(group = group, onToggle = {})
                    }
                    if (group.isExpanded) {
                        items(group.contacts, key = { it.email }) { contact ->
                            ContactItem(
                                contact = contact,
                                onClick = { onNavigateToChat(contact.email) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showAddContactDialog) {
            AlertDialog(
                onDismissRequest = viewModel::hideAddContactDialog,
                title = { Text("Add Contact") },
                text = {
                    OutlinedTextField(
                        value = addContactEmail,
                        onValueChange = { addContactEmail = it },
                        label = { Text("Email address") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.addContact(addContactEmail)
                        addContactEmail = ""
                    }) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::hideAddContactDialog) {
                        Text("Cancel")
                    }
                }
            )
        }

        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                viewModel.clearError()
            }
            Snackbar(
                action = { TextButton(onClick = viewModel::clearError) { Text("Dismiss") } }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(state: ConnectionState) {
    val (text, color) = when (state) {
        ConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING -> "Connecting..." to MaterialTheme.colorScheme.tertiary
        ConnectionState.RECONNECTING -> "Reconnecting..." to MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
