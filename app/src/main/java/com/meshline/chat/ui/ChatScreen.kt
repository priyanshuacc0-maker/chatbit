package com.meshline.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshline.chat.data.StoredMessage
import com.meshline.chat.network.Limits

private sealed interface ChatItem { val key: String }
private class DayChip(val label: String) : ChatItem { override val key: String get() = "day-$label" }
private class Msg(val message: StoredMessage) : ChatItem { override val key: String get() = message.id }

@Composable
internal fun ChatScreen(
    peerId: String,
    peerName: String,
    isOnline: Boolean,
    messages: List<StoredMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onForget: () -> Unit,
) {
    var tab by remember(peerId) { mutableIntStateOf(0) }
    var draft by remember(peerId) { mutableStateOf("") }
    var menuOpen by remember(peerId) { mutableStateOf(false) }
    var confirmForget by remember(peerId) { mutableStateOf(false) }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget $peerName?", fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("This deletes the whole conversation on this phone and removes the pairing. You will need to pair again to chat.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp) },
            confirmButton = { TextButton(onClick = { confirmForget = false; onForget() }) { Text("Forget", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel", color = Muted) } },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Column(Modifier.fillMaxSize().background(PageBrush).imePadding()) {
        // ---- header
        Row(Modifier.fillMaxWidth().background(Glass).padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink) }
            Avatar(peerName, 48.dp, isOnline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peerName, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isOnline) Green else Muted.copy(alpha = 0.5f)))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isOnline) "Online · secure direct link" else "Offline · messages queue", color = Muted, fontSize = 13.sp, maxLines = 1)
                }
            }
            IconButton(onClick = { tab = 1 }) { Icon(Icons.Filled.Shield, contentDescription = "Security details", tint = Ink) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Ink) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Forget this device", color = Danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Danger) },
                        onClick = { menuOpen = false; confirmForget = true }
                    )
                }
            }
        }
        // ---- tabs
        Row(Modifier.fillMaxWidth().background(Glass.copy(alpha = 0.6f)).padding(horizontal = 12.dp)) {
            TabPill("Chat", Icons.Filled.Forum, tab == 0) { tab = 0 }
            TabPill("Details", Icons.Filled.Info, tab == 1) { tab = 1 }
        }
        HorizontalLine()

        if (tab == 0) {
            val items = remember(messages) {
                val out = ArrayList<ChatItem>()
                var lastLabel = ""
                for (m in messages) {
                    val label = dayLabel(m.sentAt)
                    if (label != lastLabel) { out.add(DayChip(label)); lastLabel = label }
                    out.add(Msg(m))
                }
                out.reversed()
            }
            if (items.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("A private conversation", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Messages travel directly between your phones. There is no cloud inbox.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is DayChip -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                Surface(color = Color.White.copy(alpha = 0.9f), shape = CircleShape) {
                                    Text(item.label, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                                }
                            }
                            is Msg -> MessageBubble(item.message)
                        }
                    }
                }
            }
            // ---- composer
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color.White)
                        .border(1.dp, Border, RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= Limits.MAX_CHARS && it.toByteArray(Charsets.UTF_8).size <= Limits.MAX_UTF8_BYTES) draft = it },
                        modifier = Modifier.weight(1f), maxLines = 4,
                        textStyle = TextStyle(color = Ink, fontSize = 16.sp),
                        cursorBrush = SolidColor(Accent),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text("Message…", color = Muted, fontSize = 16.sp)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(10.dp))
                val canSend = draft.isNotBlank()
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(if (canSend) Accent else Accent.copy(alpha = 0.35f))
                        .clickable(enabled = canSend) {
                            val value = draft.trim()
                            if (value.isNotEmpty()) { onSend(value); draft = "" }
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White) }
            }
        } else {
            DetailsTab(peerId, peerName, isOnline, messages, onForget = { confirmForget = true })
        }
    }
}

@Composable
private fun HorizontalLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
}

@Composable
private fun TabPill(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) Accent else Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (selected) Accent else Ink, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) Accent else Color.Transparent))
    }
}

@Composable
private fun MessageBubble(message: StoredMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier.widthIn(max = 310.dp)
                .clip(RoundedCornerShape(
                    topStart = 22.dp, topEnd = 22.dp,
                    bottomStart = if (message.outgoing) 22.dp else 6.dp,
                    bottomEnd = if (message.outgoing) 6.dp else 22.dp
                ))
                .background(if (message.outgoing) AccentFill else Color.White)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(message.body, color = Ink, fontSize = 16.sp, lineHeight = 22.sp)
            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(message.sentAt), color = Muted, fontSize = 11.sp)
                if (message.outgoing) {
                    Spacer(Modifier.width(5.dp))
                    when (message.status) {
                        "DELIVERED" -> Icon(Icons.Filled.DoneAll, contentDescription = "Delivered", tint = Accent, modifier = Modifier.size(16.dp))
                        "SENT" -> Icon(Icons.Filled.Done, contentDescription = "Sent", tint = Muted, modifier = Modifier.size(16.dp))
                        "FAILED" -> Text("Not sent", color = Danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        else -> Icon(Icons.Filled.Schedule, contentDescription = "Queued", tint = Muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsTab(peerId: String, peerName: String, isOnline: Boolean, messages: List<StoredMessage>, onForget: () -> Unit) {
    val waiting = messages.count { it.outgoing && (it.status == "PENDING" || it.status == "SENT") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DetailCard("Connection", Icons.Filled.WifiTethering) {
            Text(if (isOnline) "Online — talking directly over Bluetooth / Wi-Fi." else "Offline — $peerName is out of range. Messages wait here and send when you reconnect.", color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
            if (waiting > 0) {
                Spacer(Modifier.height(6.dp))
                Text("$waiting message${if (waiting == 1) "" else "s"} waiting for delivery", color = Muted, fontSize = 13.sp)
            }
        }
        DetailCard("Security", Icons.Filled.Lock) {
            Text("End-to-end encrypted with a fresh key for every connection. Nothing goes through a server, and messages are encrypted again on this phone.", color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
        }
        DetailCard("Device ID", Icons.Filled.Shield) {
            Text(shortId(peerId), color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Text("To be extra sure this is really $peerName, compare this with the ID under “My Profile” in their menu.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
        OutlinedButton(onClick = onForget, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Danger.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
            Text("Forget this device", color = Danger, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Surface(color = Glass, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
