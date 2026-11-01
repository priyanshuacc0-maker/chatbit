package com.meshline.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshline.chat.ChatViewModel
import com.meshline.chat.data.ConversationSummary
import com.meshline.chat.data.StoredMessage
import com.meshline.chat.network.Limits
import com.meshline.chat.network.NearbyPeer
import com.meshline.chat.network.PendingPairing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF171827)
private val Muted = Color(0xFF74788B)
private val Page = Color(0xFFF7F8FC)
private val Accent = Color(0xFF6558E8)
private val AccentSoft = Color(0xFFF0EEFF)
private val Green = Color(0xFF1F9D70)
private val Border = Color(0xFFE8EAF1)

@Composable
fun MeshlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Green,
            background = Page,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        content = content
    )
}

@Composable
fun MeshlineApp(viewModel: ChatViewModel, onStartNearby: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val peers by viewModel.availablePeers.collectAsStateWithLifecycle()
    val pairings by viewModel.pendingPairings.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedPeerId by viewModel.selectedPeerId.collectAsStateWithLifecycle()
    val onlinePeers by viewModel.onlinePeerIds.collectAsStateWithLifecycle()
    val discovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val localName by viewModel.localName.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = Page) {
      // Android 15 forces edge-to-edge; keep content clear of the system bars.
      Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (selectedPeerId == null) {
            HomeScreen(
                localName = localName,
                onRename = viewModel::setDisplayName,
                status = status,
                peers = peers,
                conversations = conversations,
                onlinePeerIds = onlinePeers,
                isDiscovering = discovering,
                onStartNearby = onStartNearby,
                onStopDiscovery = viewModel::stopDiscovery,
                onConnect = viewModel::connect,
                onOpenConversation = viewModel::selectConversation
            )
        } else {
            val conversation = conversations.firstOrNull { it.peerId == selectedPeerId }
            ChatScreen(
                peerId = selectedPeerId!!,
                peerName = conversation?.displayName ?: "Nearby device",
                isOnline = onlinePeers.contains(selectedPeerId),
                messages = messages,
                onBack = viewModel::closeConversation,
                onSend = { viewModel.sendMessage(selectedPeerId!!, it) },
                onForget = { viewModel.forgetPeer(selectedPeerId!!) }
            )
        }
      }
    }

    pairings.firstOrNull()?.let { pairing ->
        PairingDialog(
            pairing = pairing,
            onAccept = { viewModel.acceptPairing(pairing.endpointId) },
            onReject = { viewModel.rejectPairing(pairing.endpointId) }
        )
    }
}

@Composable
private fun HomeScreen(
    localName: String,
    onRename: (String) -> Unit,
    status: String,
    peers: List<NearbyPeer>,
    conversations: List<ConversationSummary>,
    onlinePeerIds: Set<String>,
    isDiscovering: Boolean,
    onStartNearby: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        if (renaming) {
            RenameDialog(
                current = localName,
                onSave = { onRename(it); renaming = false },
                onDismiss = { renaming = false }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(15.dp), color = Accent, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("m", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("meshline", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
                Text("PRIVATE · NEARBY · OFFLINE", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.15.sp)
            }
            Surface(color = Color.White, shape = CircleShape, border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (onlinePeerIds.isNotEmpty()) Green else Muted.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(7.dp))
                    Text("${onlinePeerIds.size} online", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Your nearby network", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${localName.take(48)}  ·  This phone", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { renaming = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text("Rename", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = if (isDiscovering) onStopDiscovery else onStartNearby,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) {
                    if (isDiscovering) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        Text("Stop searching", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        Text("Find nearby devices", fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDiscovering) CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = Accent)
                    else Icon(Icons.Filled.Wifi, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(status, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.height(25.dp))
        SectionHeading("Nearby devices", "${peers.size} found")
        Spacer(Modifier.height(10.dp))
        if (peers.isEmpty()) {
            EmptyCard(
                title = if (isDiscovering) "Looking for nearby phones" else "No devices found yet",
                description = if (isDiscovering) "Meshline keeps searching in the background while it is running, and works without mobile data or a router." else "Tap Find nearby devices on both phones to discover each other."
            )
        } else {
            peers.forEach { peer ->
                PeerCard(peer = peer, onConnect = { onConnect(peer.endpointId) })
                Spacer(Modifier.height(9.dp))
            }
        }

        Spacer(Modifier.height(23.dp))
        SectionHeading("Your chats", "${conversations.size}")
        Spacer(Modifier.height(10.dp))
        if (conversations.isEmpty()) {
            EmptyCard(
                title = "Your conversations live here",
                description = "Connect to another Meshline phone. Messages are kept on this device and sent directly peer to peer."
            )
        } else {
            conversations.forEach { conversation ->
                ConversationCard(
                    conversation = conversation,
                    online = onlinePeerIds.contains(conversation.peerId),
                    onClick = { onOpenConversation(conversation.peerId) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("No account · No cloud relay · No phone number", color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionHeading(title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun PeerCard(peer: NearbyPeer, onConnect: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(peer.name)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (peer.known) "Paired device · reconnects automatically" else "Meshline device · nearby", color = Muted, fontSize = 10.sp)
            }
            OutlinedButton(
                onClick = onConnect,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 5.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.35f))
            ) {
                Text("Connect", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ConversationCard(conversation: ConversationSummary, online: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(conversation.displayName)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.displayName, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(3.dp))
                Text(conversation.lastMessage.ifBlank { "Start a conversation" }, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatTime(conversation.updatedAt), color = Muted, fontSize = 9.sp)
                Spacer(Modifier.height(5.dp))
                if (online) {
                    Surface(color = Color(0xFFE8F7F0), shape = CircleShape) {
                        Text("ONLINE", color = Green, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceAvatar(name: String) {
    Surface(color = AccentSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(42.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "M", color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PairingDialog(pairing: PendingPairing, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* A pairing request must be explicitly accepted or rejected. */ },
        title = { Text("Verify this device", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                Text("You are connecting to ${pairing.name}. Compare this code with the other phone. Only continue when both codes match.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(18.dp))
                Surface(color = AccentSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        pairing.verificationCode.chunked(2).joinToString(" "),
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text("This check protects the first connection from impersonation. If the other phone shows no code, tap Reject.", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("Codes match · Accept")
            }
        },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ChatScreen(
    peerId: String,
    peerName: String,
    isOnline: Boolean,
    messages: List<StoredMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onForget: () -> Unit,
) {
    var draft by remember(peerId) { mutableStateOf("") }
    var confirmForget by remember(peerId) { mutableStateOf(false) }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget $peerName?", fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("This deletes the whole conversation on this phone and removes the pairing. You will need to pair again to chat.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp) },
            confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Forget", color = Color(0xFFD64545)) } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel", color = Muted) } },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
    Column(Modifier.fillMaxSize().background(Page).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            DeviceAvatar(peerName)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(peerName, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (isOnline) Green else Muted.copy(alpha = 0.45f)))
                    Spacer(Modifier.width(5.dp))
                    Text(if (isOnline) "Secure direct connection" else "Offline · messages queue", color = Muted, fontSize = 10.sp)
                }
            }
            Icon(Icons.Filled.Lock, contentDescription = "Encrypted", tint = Green, modifier = Modifier.size(18.dp))
            IconButton(onClick = { confirmForget = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Forget this device", tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = Border)
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Surface(color = AccentSoft, shape = CircleShape, modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(25.dp))
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Text("A private conversation", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Messages travel directly between your phones. There is no cloud inbox.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                reverseLayout = true,
                contentPadding = PaddingValues(top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages.reversed(), key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        if (it.length <= Limits.MAX_CHARS && it.toByteArray(Charsets.UTF_8).size <= Limits.MAX_UTF8_BYTES) draft = it
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message…", fontSize = 13.sp, color = Muted) },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 4
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val value = draft.trim()
                        if (value.isNotEmpty()) {
                            onSend(value)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.size(48.dp).background(if (draft.isNotBlank()) Accent else Accent.copy(alpha = 0.25f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: StoredMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (message.outgoing) 18.dp else 5.dp,
                    bottomEnd = if (message.outgoing) 5.dp else 18.dp
                ))
                .background(if (message.outgoing) Accent else Color.White)
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(message.body, color = if (message.outgoing) Color.White else Ink, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(formatTime(message.sentAt), color = if (message.outgoing) Color.White.copy(alpha = 0.7f) else Muted, fontSize = 9.sp)
                if (message.outgoing) {
                    Spacer(Modifier.width(4.dp))
                    when (message.status) {
                        "DELIVERED" -> Icon(Icons.Filled.CheckCircle, contentDescription = "Delivered", tint = Color(0xFFBDF6DC), modifier = Modifier.size(12.dp))
                        "SENT" -> Icon(Icons.Filled.Check, contentDescription = "Sent", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(12.dp))
                        "FAILED" -> Text("Not sent", color = Color(0xFFFFD0D0), fontSize = 8.sp)
                        else -> Text("Queued", color = Color.White.copy(alpha = 0.72f), fontSize = 8.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                Text("Nearby people see this name when you search.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { if (it.length <= 32) value = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) {
                Text("Save", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
