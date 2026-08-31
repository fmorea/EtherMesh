package com.fmorea.syncthing.syncthing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fmorea.syncthing.model.Device
import java.io.File

@Composable
fun NetworkView(
    viewModel: LinkThingViewModel,
    onEditMyProfile: () -> Unit,
    onEditFriendProfile: (String) -> Unit,
    onShowGraph: () -> Unit
) {
    val friends by viewModel.friends.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()
    val topology by viewModel.meshTopology.collectAsState()
    val discoveredIds by viewModel.discoveredDevices.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val friendProfiles by viewModel.friendProfiles.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    
    val deviceNames = remember(friends, localDevice, userProfile, friendProfiles) {
        val map = friends.associate { it.deviceID to (friendProfiles[it.deviceID]?.getDisplayName() ?: it.getDisplayName()) }.toMutableMap()
        localDevice?.let { map[it.deviceID] = userProfile.getDisplayName() }
        map
    }

    var showConfirmDelete by remember { mutableStateOf<String?>(null) }
    var viewingIdentitiesForDeviceId by remember { mutableStateOf<String?>(null) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    if (showManualAddDialog) {
        AddFriendDialog(
            onDismiss = { showManualAddDialog = false },
            onAddFriend = { deviceId ->
                viewModel.addFriend(deviceId)
                showManualAddDialog = false
            },
            onScanQrCode = {
                showManualAddDialog = false
                viewModel.scanQrCode()
            }
        )
    }

    if (showConfirmDelete != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = null },
            title = { Text("Rimuovi dal Network") },
            text = { Text("Sei sicuro di voler rimuovere questo dispositivo? Non potrai più scambiare messaggi con lui.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFriend(showConfirmDelete!!)
                        showConfirmDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Rimuovi") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = null }) { Text("Annulla") }
            }
        )
    }

    if (viewingIdentitiesForDeviceId != null) {
        val targetDeviceId = viewingIdentitiesForDeviceId!!
        val profiles = allProfiles[targetDeviceId] ?: emptyList()
        AlertDialog(
            onDismissRequest = { viewingIdentitiesForDeviceId = null },
            title = { Text("Gestione Identità") },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(profiles) { profile ->
                        val isOwner = profile.discloserId == targetDeviceId
                        val isVerifiedByMe = profile.discloserId == localDevice?.deviceID
                        
                        val identityType = when {
                            isOwner -> "Identità Autodeterminata"
                            isVerifiedByMe -> "Identità Verificata (da te)"
                            else -> "Identità Segnalata da: ${deviceNames[profile.discloserId] ?: profile.discloserId.take(8)}"
                        }

                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.getDisplayName())
                                    if (isVerifiedByMe) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            supportingContent = { Text(identityType) },
                            trailingContent = {
                                Row {
                                    if (!isVerifiedByMe) {
                                        IconButton(onClick = { viewModel.updateFriendProfile(targetDeviceId, profile) }) {
                                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteIdentity(targetDeviceId, profile.discloserId) }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingIdentitiesForDeviceId = null }) { Text("Chiudi") }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader("IL MIO PROFILO")
        }

        item {
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth().widthIn(max = 800.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(deviceId = localDevice?.deviceID ?: "", profile = userProfile, size = 72, onClick = onEditMyProfile)
                    Spacer(modifier = Modifier.width(20.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile.getDisplayName(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "La tua identità nel network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.showMyId() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.QrCode, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Il mio QR", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        OutlinedButton(
                            onClick = { viewingIdentitiesForDeviceId = localDevice?.deviceID },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Badge, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Identità", style = MaterialTheme.typography.labelMedium)
                        }
                        
                        OutlinedButton(
                            onClick = onShowGraph,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Hub, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mappa", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    }
                }
            }
        }

        item {
            SectionHeader("DISPOSITIVI CONNESSI")
        }

        item {
            OutlinedButton(
                onClick = { showManualAddDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aggiungi Dispositivo")
            }
        }

        if (friends.isEmpty()) {
            item {
                Text(
                    "Nessun altro dispositivo nel network mesh.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(friends, key = { it.deviceID }) { device ->
                DeviceItem(
                    device = device,
                    isMe = false,
                    profile = friendProfiles[device.deviceID],
                    myDeviceId = localDevice?.deviceID ?: "",
                    allProfiles = allProfiles[device.deviceID] ?: emptyList(),
                    introducedBy = topology[device.deviceID],
                    deviceNames = deviceNames,
                    onDelete = { showConfirmDelete = device.deviceID },
                    onEditProfile = { onEditFriendProfile(device.deviceID) },
                    onViewIdentities = { viewingIdentitiesForDeviceId = device.deviceID },
                    onVerify = { viewModel.updateFriendProfile(device.deviceID, it) },
                    onTogglePause = { viewModel.toggleDevicePause(device.deviceID) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }

        if (discoveredIds.isNotEmpty()) {
            item {
                SectionHeader("DISPOSITIVI SCOPERTI (MESH)")
            }
            items(discoveredIds.toList()) { deviceId ->
                DiscoveredDeviceItem(
                    deviceId = deviceId,
                    introducedBy = topology[deviceId],
                    deviceNames = deviceNames,
                    onAdd = { viewModel.addFriend(deviceId) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
