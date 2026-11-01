package com.meshline.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.meshline.chat.ChatViewModel
import com.meshline.chat.network.PendingPairing
import kotlinx.coroutines.launch

@Composable
fun MeshlineApp(
    viewModel: ChatViewModel,
    onStartNearby: () -> Unit,
    onScanQr: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val peers by viewModel.availablePeers.collectAsStateWithLifecycle()
    val pairings by viewModel.pendingPairings.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val selectedPeerId by viewModel.selectedPeerId.collectAsStateWithLifecycle()
    val onlinePeers by viewModel.onlinePeerIds.collectAsStateWithLifecycle()
    val discovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val localName by viewModel.localName.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val connecting by viewModel.connecting.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var renaming by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }
    var inviteText by remember { mutableStateOf<String?>(null) }

    fun closeDrawer() { scope.launch { drawerState.close() } }
    fun openDrawer() { scope.launch { drawerState.open() } }
    fun showQr() {
        if (!discovering) onStartNearby()
        inviteText = viewModel.createInvite()
    }
    fun turnOn() { onStartNearby() }

    BackHandler(enabled = drawerState.isOpen) { closeDrawer() }
    BackHandler(enabled = selectedPeerId != null && !drawerState.isOpen) { viewModel.closeConversation() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = selectedPeerId == null,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xFFF1F6FD)) {
                MenuContent(
                    localName = localName,
                    localId = viewModel.localDeviceId,
                    filter = filter,
                    unreadTotal = unread.values.sum(),
                    onlineCount = conversations.count { onlinePeers.contains(it.peerId) },
                    nearbyCount = peers.size,
                    chatCount = conversations.size,
                    discovering = discovering,
                    status = status,
                    onFilter = { filter = it; closeDrawer() },
                    onSecurity = { closeDrawer(); showSecurity = true },
                    onShowQr = { closeDrawer(); showQr() },
                    onScanQr = { closeDrawer(); onScanQr() },
                    onRename = { closeDrawer(); renaming = true },
                    onToggleSearch = { closeDrawer(); if (discovering) viewModel.stopDiscovery() else turnOn() },
                    onBattery = { closeDrawer(); onBatterySettings() },
                    onStopAll = { closeDrawer(); viewModel.stopAll() },
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(PageBrush).systemBarsPadding()) {
            val openId = selectedPeerId
            if (openId == null) {
                HomeScreen(
                    filter = filter,
                    onFilter = { filter = it },
                    conversations = conversations,
                    unread = unread,
                    onlinePeerIds = onlinePeers,
                    peers = peers,
                    connecting = connecting,
                    status = status,
                    discovering = discovering,
                    onMenu = { openDrawer() },
                    onNewChat = { filter = HomeFilter.NEARBY; if (!discovering) turnOn() },
                    onTurnOn = { turnOn() },
                    onShowQr = { showQr() },
                    onConnect = viewModel::connect,
                    onOpenConversation = viewModel::selectConversation,
                )
            } else {
                val conversation = conversations.firstOrNull { it.peerId == openId }
                ChatScreen(
                    peerId = openId,
                    peerName = conversation?.displayName ?: "Nearby device",
                    isOnline = onlinePeers.contains(openId),
                    messages = messages,
                    onBack = viewModel::closeConversation,
                    onSend = { viewModel.sendMessage(openId, it) },
                    onForget = { viewModel.forgetPeer(openId) }
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
    if (renaming) {
        RenameDialog(current = localName, onSave = { viewModel.setDisplayName(it); renaming = false }, onDismiss = { renaming = false })
    }
    if (showSecurity) SecurityDialog { showSecurity = false }
    inviteText?.let { text ->
        InviteDialog(
            text = text,
            onScan = { inviteText = null; viewModel.cancelInvite(); onScanQr() },
            onClose = { inviteText = null; viewModel.cancelInvite() }
        )
    }
}

// ---------------------------------------------------------------------------------- dialogs

@Composable
private fun PairingDialog(pairing: PendingPairing, onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* A pairing request must be explicitly accepted or rejected. */ },
        title = { Text("Pair with ${pairing.name}?", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                Text("Both phones should show this same code.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Surface(color = AccentSoft, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        pairing.verificationCode.chunked(2).joinToString(" "),
                        modifier = Modifier.padding(vertical = 18.dp),
                        color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("This quick check stops anyone else from pretending to be them. Different code? Tap Reject.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = Accent), shape = RoundedCornerShape(14.dp)) {
                Text("Codes match")
            }
        },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun InviteDialog(text: String, onScan: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pair with a QR code", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 2.dp) {
                    QrImage(text, Modifier.padding(10.dp).size(220.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Optional. On the other phone open Meshline and tap Scan QR. Works once, for 5 minutes, and no code comparison is needed.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done", color = Accent, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onScan) { Text("Scan instead", color = Muted) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun QrImage(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 1, 1, mapOf(EncodeHintType.MARGIN to 1))
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.width / n
        for (y in 0 until n) {
            for (x in 0 until n) {
                if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
    }
}

@Composable
private fun SecurityDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy & security", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Text(
                "• No account, phone number or server — phones talk directly.\n" +
                "• Every connection uses a fresh encryption key, signed by each phone's own secure key.\n" +
                "• Saved messages are encrypted again on this phone.\n" +
                "• Paired phones reconnect automatically and are dropped if their identity changes.\n" +
                "• Pairing is protected by the matching code or a one-time QR.",
                color = Ink, fontSize = 14.sp, lineHeight = 22.sp
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = Accent, fontWeight = FontWeight.SemiBold) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name", fontWeight = FontWeight.Bold, color = Ink) },
        text = {
            Column {
                Text("Nearby people see this name. It applies the next time you search.", color = Muted, fontSize = 13.sp)
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
