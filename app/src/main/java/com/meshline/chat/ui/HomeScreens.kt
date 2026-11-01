package com.meshline.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshline.chat.data.ConversationSummary
import com.meshline.chat.network.NearbyPeer
import kotlinx.coroutines.delay

enum class HomeFilter { ALL, UNREAD, ONLINE, NEARBY }

// ------------------------------------------------------------------------------------ menu

@Composable
internal fun MenuContent(
    localName: String,
    localId: String,
    filter: HomeFilter,
    unreadTotal: Int,
    onlineCount: Int,
    nearbyCount: Int,
    chatCount: Int,
    discovering: Boolean,
    status: String,
    onFilter: (HomeFilter) -> Unit,
    onSecurity: () -> Unit,
    onShowQr: () -> Unit,
    onScanQr: () -> Unit,
    onRename: () -> Unit,
    onToggleSearch: () -> Unit,
    onBattery: () -> Unit,
    onStopAll: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Text("m", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text("Meshline", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Surface(color = AccentSoft, shape = RoundedCornerShape(10.dp)) {
                Text("Offline", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onScanQr) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR", tint = Ink) }
        }
        Spacer(Modifier.height(14.dp))
        MenuItem(Icons.Filled.Forum, "Chats", filter == HomeFilter.ALL || filter == HomeFilter.UNREAD, unreadTotal) { onFilter(HomeFilter.ALL) }
        MenuItem(Icons.Filled.Wifi, "Online now", filter == HomeFilter.ONLINE, onlineCount) { onFilter(HomeFilter.ONLINE) }
        MenuItem(Icons.Filled.WifiTethering, "Nearby devices", filter == HomeFilter.NEARBY, nearbyCount) { onFilter(HomeFilter.NEARBY) }
        MenuItem(Icons.Filled.Shield, "Privacy & security", false, 0, onSecurity)

        Spacer(Modifier.height(18.dp))
        Text("Tools", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(Icons.Filled.QrCode2, "Show QR", Modifier.weight(1f), onShowQr)
            ToolTile(Icons.Filled.QrCodeScanner, "Scan QR", Modifier.weight(1f), onScanQr)
            ToolTile(Icons.Filled.DriveFileRenameOutline, "Rename", Modifier.weight(1f), onRename)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(Icons.Filled.WifiTethering, if (discovering) "Pause search" else "Find nearby", Modifier.weight(1f), onToggleSearch)
            ToolTile(Icons.Filled.BatteryChargingFull, "Keep alive", Modifier.weight(1f), onBattery)
            ToolTile(Icons.Filled.PowerSettingsNew, "Stop all", Modifier.weight(1f), onStopAll)
        }

        Spacer(Modifier.height(20.dp))
        Surface(color = Glass, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Wifi, contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Nearby radio", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(status, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (discovering) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = Accent, trackColor = AccentSoft)
                } else {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(AccentSoft))
                }
                Spacer(Modifier.height(8.dp))
                Text("$chatCount chats on this phone · encrypted at rest", color = Muted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = Border)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onRename).padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(localName, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("My Profile", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("$localName · ${shortId(localId)}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Ink)
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, selected: Boolean, badge: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentFill else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Accent else Ink, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(label, color = Ink, fontSize = 17.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (badge > 0) CountBadge(badge)
    }
}

@Composable
private fun ToolTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(color = Glass, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Border), modifier = modifier.clickable(onClick = onClick)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = Ink, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = Ink, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun CountBadge(count: Int) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = if (count > 99) 9.sp else 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------------------- chat list

@Composable
internal fun HomeScreen(
    filter: HomeFilter,
    onFilter: (HomeFilter) -> Unit,
    conversations: List<ConversationSummary>,
    unread: Map<String, Int>,
    onlinePeerIds: Set<String>,
    peers: List<NearbyPeer>,
    connecting: Set<String>,
    status: String,
    discovering: Boolean,
    problem: String?,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    onTurnOn: () -> Unit,
    onShowQr: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val unreadTotal = unread.values.sum()
    val onlineCount = conversations.count { onlinePeerIds.contains(it.peerId) }
    val shownChats = conversations.filter { c ->
        (q.isEmpty() || c.displayName.lowercase().contains(q)) && when (filter) {
            HomeFilter.UNREAD -> (unread[c.peerId] ?: 0) > 0
            HomeFilter.ONLINE -> onlinePeerIds.contains(c.peerId)
            else -> true
        }
    }
    val shownPeers = peers.filter { q.isEmpty() || it.name.lowercase().contains(q) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Ink) }
                Row(
                    Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(26.dp)).background(Glass)
                        .border(1.dp, Border, RoundedCornerShape(26.dp)).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query, onValueChange = { if (it.length <= 48) query = it },
                        singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(Accent),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search chats and devices…", color = Muted, fontSize = 15.sp)
                            inner()
                        }
                    )
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChipPill("All", filter == HomeFilter.ALL, 0) { onFilter(HomeFilter.ALL) }
                FilterChipPill("Unread", filter == HomeFilter.UNREAD, unreadTotal) { onFilter(HomeFilter.UNREAD) }
                FilterChipPill("Online", filter == HomeFilter.ONLINE, onlineCount) { onFilter(HomeFilter.ONLINE) }
                FilterChipPill("Nearby", filter == HomeFilter.NEARBY, peers.size) { onFilter(HomeFilter.NEARBY) }
            }
            StatusStrip(status, discovering, onTurnOn)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 110.dp)) {
                if (problem != null) item(key = "problem") { ProblemCard(problem, onRestart) }
                if (filter == HomeFilter.NEARBY || filter == HomeFilter.ALL) {
                    // Nearby phones are always on the main list, so connecting never hides behind a tab.
                    if (shownPeers.isNotEmpty()) {
                        item(key = "nearby-label") { SectionLabel("Nearby now · ${shownPeers.size} — tap to connect") }
                        items(shownPeers, key = { "peer-" + it.endpointId }) { peer ->
                            PeerRow(peer, connecting.contains(peer.endpointId)) { onConnect(peer.endpointId) }
                            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 86.dp))
                        }
                    } else if (q.isEmpty()) {
                        item(key = "searching") { SearchingCard(discovering, onTurnOn, onRestart) }
                    }
                }
                if (filter != HomeFilter.NEARBY) {
                    if (shownChats.isNotEmpty()) {
                        if (filter == HomeFilter.ALL) item(key = "chats-label") { SectionLabel("Chats") }
                        items(shownChats, key = { "chat-" + it.peerId }) { c ->
                            ConversationRow(c, onlinePeerIds.contains(c.peerId), unread[c.peerId] ?: 0) { onOpenConversation(c.peerId) }
                            HorizontalDivider(color = Border, modifier = Modifier.padding(start = 86.dp))
                        }
                    } else if (conversations.isNotEmpty() || filter != HomeFilter.ALL) {
                        item(key = "empty") { EmptyState(title = "Nothing here", body = "No chats match this view.", primary = null, secondary = null) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewChat, containerColor = Accent, contentColor = Color.White, shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 26.dp).size(64.dp)
        ) { Icon(Icons.Filled.Edit, contentDescription = "New chat") }
    }
}

@Composable
private fun FilterChipPill(label: String, selected: Boolean, count: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(if (selected) Accent else Glass)
            .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (selected) Color.White else Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (count > 0) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(22.dp).clip(CircleShape).background(if (selected) Color.White.copy(alpha = 0.25f) else Color(0xFFDDE3EC)), contentAlignment = Alignment.Center) {
                Text(count.toString(), color = if (selected) Color.White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusStrip(status: String, discovering: Boolean, onTurnOn: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (discovering) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = Accent)
        else Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(status, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (!discovering) TextButton(onClick = onTurnOn, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Text("Turn on", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConversationRow(c: ConversationSummary, online: Boolean, unread: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(c.displayName, 56.dp, online)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(c.displayName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                c.lastMessage.ifBlank { "Say hello" }, color = Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread > 0) FontWeight.Medium else FontWeight.Normal
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatStamp(c.updatedAt), color = if (unread > 0) Accent else Muted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            if (unread > 0) CountBadge(unread) else Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun PeerRow(peer: NearbyPeer, connecting: Boolean, onConnect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !connecting, onClick = onConnect).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(peer.name, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (peer.known) "Paired · reconnects automatically" else "Meshline phone nearby", color = Muted, fontSize = 13.sp)
        }
        if (connecting) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
        } else {
            Button(onClick = onConnect, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                Text(if (peer.known) "Open" else "Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
}

@Composable
private fun ProblemCard(message: String, onRetry: () -> Unit) {
    Surface(
        color = Color(0xFFFFF1F0), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Danger.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Search problem", color = Danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(message, color = Ink, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("Try again", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Shown while nobody is visible yet. After 15 s it explains what to check instead of just spinning. */
@Composable
private fun SearchingCard(discovering: Boolean, onTurnOn: () -> Unit, onRestart: () -> Unit) {
    var slow by remember(discovering) { mutableStateOf(false) }
    LaunchedEffect(discovering) {
        slow = false
        if (discovering) { delay(15_000); slow = true }
    }
    Surface(
        color = Glass, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (discovering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Accent)
                else Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (discovering) "Looking for nearby phones" else "Nearby is off", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (discovering) "Open Meshline on the other phone — it will appear right here to tap and connect."
                        else "Turn search on to find phones that have Meshline open.",
                        color = Muted, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
            if (!discovering) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onTurnOn, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Turn on", fontWeight = FontWeight.SemiBold)
                }
            } else if (slow) {
                Spacer(Modifier.height(12.dp))
                Text("Nobody showing up? Check on BOTH phones:", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "• Meshline is open and search is on\n• Bluetooth and Wi-Fi are on\n• Location is on (some phones need it)\n• The phones are within a few metres",
                    color = Muted, fontSize = 13.sp, lineHeight = 20.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRestart, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Accent.copy(alpha = 0.4f))) {
                    Text("Restart search", color = Accent)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, primary: Pair<String, () -> Unit>?, secondary: Pair<String, () -> Unit>?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.WifiTethering, contentDescription = null, tint = Accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        if (primary != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = primary.second, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text(primary.first, fontWeight = FontWeight.SemiBold)
            }
        }
        if (secondary != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = secondary.second, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Accent.copy(alpha = 0.4f))) {
                Text(secondary.first, color = Accent)
            }
        }
    }
}
