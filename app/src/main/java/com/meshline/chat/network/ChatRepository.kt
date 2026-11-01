package com.meshline.chat.network

import android.content.Context
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.meshline.chat.data.ChatDatabase
import com.meshline.chat.data.ConversationSummary
import com.meshline.chat.data.StoredMessage
import com.meshline.chat.notify.MessageNotifier
import com.meshline.chat.security.DeviceIdentity
import com.meshline.chat.security.Identity
import com.meshline.chat.security.PairInvite
import com.meshline.chat.security.SessionCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.spec.SecretKeySpec

data class NearbyPeer(val endpointId: String, val name: String, val known: Boolean = false)
data class PendingPairing(val endpointId: String, val name: String, val verificationCode: String)

private class PacketTooLargeException : Exception("Message is too large")
private class ActiveInvite(val token: ByteArray, val expiresAt: Long)
private data class ParsedName(val name: String, val prefix: String?, val qr: Boolean)

/**
 * Process-scoped engine: Nearby lifecycle, signed key exchange, encrypted protocol, outbox/retry.
 *
 * Trust model
 *  - A device ID is the SHA-256 of its long-term public key; it cannot be claimed without the key.
 *  - First contact requires both users to accept and compare the Nearby verification code.
 *  - Every session key exchange is signed by the long-term key, so a paired contact reconnects
 *    automatically and nobody can impersonate it (an imposter fails the identity check).
 */
class ChatRepository(context: Context) {
    companion object {
        private const val SERVICE_ID = "com.meshline.chat"
        private const val PREFS = "meshline_identity"
        private const val NAME_SEP = '\u001F'
        private const val ID_PREFIX_LEN = 32
        private const val MAX_ADVERTISED_NAME_BYTES = 40
        private const val IDLE_STOP_MS = 10 * 60_000L
        private const val CONNECTED_STOP_MS = 2 * 60_000L
        private const val INVITE_TTL_MS = 5 * 60_000L
        private const val JOIN_WAIT_MS = 45_000L
        private const val QR_HINT = "q"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val identity = DeviceIdentity()
    val localDeviceId: String = identity.id

    private val _localName = MutableStateFlow(
        prefs.getString("display_name", null)?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(32).ifBlank { "Android phone" }
    )
    val localName: StateFlow<String> = _localName.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = ChatDatabase(appContext)
    private val client = Nearby.getConnectionsClient(appContext)

    private val _status = MutableStateFlow("Offline · ready to connect")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()
    private val _availablePeers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val availablePeers: StateFlow<List<NearbyPeer>> = _availablePeers.asStateFlow()
    private val _pendingPairings = MutableStateFlow<List<PendingPairing>>(emptyList())
    val pendingPairings: StateFlow<List<PendingPairing>> = _pendingPairings.asStateFlow()
    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()
    private val _messages = MutableStateFlow<List<StoredMessage>>(emptyList())
    val messages: StateFlow<List<StoredMessage>> = _messages.asStateFlow()
    private val _selectedPeerId = MutableStateFlow<String?>(null)
    val selectedPeerId: StateFlow<String?> = _selectedPeerId.asStateFlow()
    private val _onlinePeerIds = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeerIds: StateFlow<Set<String>> = _onlinePeerIds.asStateFlow()
    private val _unread = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unread: StateFlow<Map<String, Int>> = _unread.asStateFlow()
    private val _connecting = MutableStateFlow<Set<String>>(emptySet())
    val connecting: StateFlow<Set<String>> = _connecting.asStateFlow()
    private val _inviteActive = MutableStateFlow(false)
    val inviteActive: StateFlow<Boolean> = _inviteActive.asStateFlow()
    val searchPausedByUser: Boolean get() = prefs.getBoolean("paused", false)

    /** True while discovery is running or at least one peer is connected. Drives the service. */
    val isActive: StateFlow<Boolean> = combine(_isDiscovering, _onlinePeerIds) { discovering, online ->
        discovering || online.isNotEmpty()
    }.stateIn(scope, SharingStarted.Eagerly, false)

    @Volatile private var appVisible = false

    // endpointId-keyed handshake/session state
    private val endpointNames = ConcurrentHashMap<String, String>()
    private val peerDisplay = ConcurrentHashMap<String, String>()
    private val peerIdByEndpoint = ConcurrentHashMap<String, String>()      // set once hello verified
    private val peerIdentityKeys = ConcurrentHashMap<String, ByteArray>()
    private val expectedPeerByEndpoint = ConcurrentHashMap<String, String>() // auto-accepted contacts
    private val pendingKeyPackets = ConcurrentHashMap<String, JSONObject>()
    private val ephemeralKeys = ConcurrentHashMap<String, java.security.KeyPair>()
    private val earlyPackets = ConcurrentHashMap<String, MutableList<ByteArray>>()
    private val sessionKeys = ConcurrentHashMap<String, SecretKeySpec>()
    private val requestedEndpoints: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val handshakeLock = Any()
    @Volatile private var myInvite: ActiveInvite? = null          // QR we are showing
    @Volatile private var joinTarget: PairInvite.Invite? = null   // QR we scanned
    private val qrProvisional: MutableSet<String> = ConcurrentHashMap.newKeySet<String>()
    private val endpointPrefixes = ConcurrentHashMap<String, String>()

    private val endpointByPeerId = ConcurrentHashMap<String, String>()       // set once session ready
    private val retryJobs = ConcurrentHashMap<String, Job>()
    private val retryLock = Any()
    @Volatile private var autoStopJob: Job? = null

    // Packets are processed strictly in order, one at a time, so the handshake cannot race itself.
    private val inbox = Channel<Pair<String, ByteArray>>(256)

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            if (bytes.size > Limits.MAX_PACKET_BYTES) {
                _status.value = "Ignored an oversized packet"
                return
            }
            inbox.trySend(endpointId to bytes)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val lifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (name, prefix, qrHint) = parseEndpointName(info.endpointName)
            endpointNames[endpointId] = name
            val digits = info.authenticationDigits
            scope.launch {
                val knownId = prefix?.let { db.peerIdForPrefix(it) }
                val target = joinTarget
                val joinId = if (target != null && prefix != null && target.deviceId.startsWith(prefix)) target.deviceId else null
                val invite = myInvite?.takeIf { it.expiresAt > System.currentTimeMillis() }
                when {
                    knownId != null -> {
                        // Paired contact: skip the code. Its identity is verified cryptographically
                        // during the handshake and the connection is dropped if it does not match.
                        expectedPeerByEndpoint[endpointId] = knownId
                        _status.value = "Reconnecting to $name"
                        acceptSilently(endpointId)
                    }
                    joinId != null -> {
                        // We scanned this device's QR: its identity is checked in the handshake.
                        expectedPeerByEndpoint[endpointId] = joinId
                        _status.value = "Pairing with $name"
                        acceptSilently(endpointId)
                    }
                    qrHint && invite != null -> {
                        // Someone claims to have scanned our QR; they must prove it in the handshake.
                        qrProvisional.add(endpointId)
                        _status.value = "$name scanned your QR · pairing"
                        acceptSilently(endpointId)
                    }
                    else -> {
                        _pendingPairings.update { list ->
                            list.filterNot { it.endpointId == endpointId } + PendingPairing(endpointId, name, digits)
                        }
                        _status.value = "Check that both phones show the same code"
                    }
                }
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            dropRequest(endpointId)
            _pendingPairings.update { list -> list.filterNot { it.endpointId == endpointId } }
            if (resolution.status.isSuccess) {
                _status.value = "Connected · securing your session"
                beginSecureSession(endpointId)
            } else {
                _status.value = "Connection failed (${resolution.status.statusCode})"
                cleanupEndpoint(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            cleanupEndpoint(endpointId)
            _status.value = if (endpointByPeerId.isEmpty()) "Peer disconnected" else "Connected to nearby device(s)"
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (name, prefix) = parseEndpointName(info.endpointName)
            endpointNames[endpointId] = name
            if (prefix != null) endpointPrefixes[endpointId] = prefix
            scope.launch {
                val knownId = prefix?.let { db.peerIdForPrefix(it) }
                val peer = NearbyPeer(endpointId, name, known = knownId != null)
                _availablePeers.update { list ->
                    (list.filterNot { it.endpointId == endpointId } + peer).sortedBy { it.name.lowercase() }
                }
                val target = joinTarget
                if (target != null && prefix != null && target.deviceId.startsWith(prefix) &&
                    !endpointByPeerId.containsKey(target.deviceId)
                ) {
                    // We scanned this phone's QR: connect right away, no tapping needed.
                    requestConnectionTo(endpointId, manual = false, qr = true)
                } else if (knownId != null && !endpointByPeerId.containsKey(knownId) && localDeviceId < knownId) {
                    // Both sides discover each other; only the lower ID initiates so they never collide.
                    requestConnectionTo(endpointId, manual = false)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            endpointPrefixes.remove(endpointId)
            dropRequest(endpointId)
            _availablePeers.update { list -> list.filterNot { it.endpointId == endpointId } }
        }
    }

    init {
        scope.launch { for (item in inbox) handlePacket(item.first, item.second) }
        scope.launch { refreshConversations() }
    }

    // ---------------------------------------------------------------- public API

    fun reportPermissionDenied() {
        _status.value = "Nearby permissions are required to discover and connect to devices"
    }

    fun reportStatus(message: String) { _status.value = message }

    fun setAppVisible(visible: Boolean) {
        appVisible = visible
        if (visible) {
            // Stay discoverable for as long as the app is on screen; the battery timer is for the background.
            autoStopJob?.cancel()
            _selectedPeerId.value?.let { markRead(it); scope.launch { refreshConversations() } }
        } else if (_isDiscovering.value) {
            scheduleAutoStop(IDLE_STOP_MS)
        }
    }

    fun setDisplayName(name: String) {
        val clean = TextRules.sanitizeName(name, fallback = _localName.value)
        prefs.edit().putString("display_name", clean).apply()
        _localName.value = clean
        _status.value = "Name updated · applies the next time you search"
    }

    fun startNearby() {
        prefs.edit().putBoolean("paused", false).apply()
        // Idempotent: tapping again must not leave stale sessions after a partial failure.
        client.stopDiscovery()
        client.stopAdvertising()
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        client.startAdvertising(advertisedName(), SERVICE_ID, lifecycleCallback, advertisingOptions)
            .addOnSuccessListener { _status.value = "Making this phone discoverable" }
            .addOnFailureListener { _status.value = "Advertising unavailable: ${it.message ?: "check permissions and radios"}" }
        client.startDiscovery(SERVICE_ID, discoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                _isDiscovering.value = true
                _status.value = "Searching nearby · no internet required"
                scheduleAutoStop(IDLE_STOP_MS)
            }
            .addOnFailureListener {
                _isDiscovering.value = false
                _status.value = "Discovery unavailable: ${it.message ?: "check permissions and radios"}"
            }
    }

    fun stopDiscovery(byUser: Boolean = false) {
        if (byUser) prefs.edit().putBoolean("paused", true).apply()
        autoStopJob?.cancel()
        client.stopDiscovery()
        client.stopAdvertising()
        _isDiscovering.value = false
        _availablePeers.value = emptyList()
        clearRequests()
        _status.value = if (endpointByPeerId.isEmpty()) "Offline · ready to connect" else "Connected · discovery paused"
    }

    /** Full stop: discovery, every connection and all retry loops. Used by the notification action. */
    fun stopAll() {
        autoStopJob?.cancel()
        client.stopDiscovery()
        client.stopAdvertising()
        client.stopAllEndpoints()
        (peerIdByEndpoint.keys + ephemeralKeys.keys + sessionKeys.keys).toSet().forEach { cleanupEndpoint(it) }
        synchronized(retryLock) {
            retryJobs.values.forEach { it.cancel() }
            retryJobs.clear()
        }
        _isDiscovering.value = false
        _availablePeers.value = emptyList()
        _pendingPairings.value = emptyList()
        clearRequests()
        _status.value = "Offline · ready to connect"
    }

    fun connect(endpointId: String) {
        if (sessionKeys.containsKey(endpointId)) return
        requestConnectionTo(endpointId, manual = true)
    }

    fun acceptPairing(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { _status.value = "Could not accept connection: ${it.message ?: "try again"}" }
        _pendingPairings.update { list -> list.filterNot { it.endpointId == endpointId } }
        _status.value = "Waiting for the other device to confirm"
    }

    fun rejectPairing(endpointId: String) {
        client.rejectConnection(endpointId)
        _pendingPairings.update { list -> list.filterNot { it.endpointId == endpointId } }
        _status.value = "Connection rejected"
    }

    fun sendMessage(peerId: String, text: String) {
        val body = TextRules.sanitizeBody(text)
        if (body.isEmpty()) return
        if (!TextRules.fits(body)) {
            _status.value = "Message too long · limit is ${Limits.MAX_CHARS} characters"
            return
        }
        val message = StoredMessage(
            id = UUID.randomUUID().toString(), peerId = peerId, senderId = localDeviceId,
            body = body, sentAt = System.currentTimeMillis(), status = "PENDING", outgoing = true
        )
        scope.launch {
            try {
                db.insertMessage(message)
                refreshMessages(peerId)
                refreshConversations()
                val endpointId = endpointByPeerId[peerId]
                if (endpointId != null && sessionKeys.containsKey(endpointId)) sendStored(message, endpointId)
                else _status.value = "Message queued · waiting for the peer to reconnect"
            } catch (e: Exception) {
                _status.value = "Message could not be saved: ${e.message ?: "local storage error"}"
            }
        }
    }

    fun selectConversation(peerId: String) {
        _messages.value = emptyList()
        _selectedPeerId.value = peerId
        MessageNotifier.cancel(appContext, peerId)
        markRead(peerId)
        scope.launch { refreshMessages(peerId); refreshConversations() }
    }

    fun closeConversation() {
        _selectedPeerId.value?.let { markRead(it); scope.launch { refreshConversations() } }
        _selectedPeerId.value = null
        _messages.value = emptyList()
    }

    /** Deletes the conversation and revokes trust: the device must be paired again to chat. */
    fun forgetPeer(peerId: String) {
        endpointByPeerId[peerId]?.let { endpoint ->
            client.disconnectFromEndpoint(endpoint)
            cleanupEndpoint(endpoint)
        }
        closeConversation()
        scope.launch {
            db.deletePeer(peerId)
            refreshConversations()
            _status.value = "Device forgotten · pairing is required to chat again"
        }
    }

    // ---------------------------------------------------------------- optional QR pairing

    /** Starts a 5-minute, single-use invite and returns the text to render as a QR code. */
    fun createInvite(): String {
        val token = PairInvite.newToken()
        myInvite = ActiveInvite(token, System.currentTimeMillis() + INVITE_TTL_MS)
        _inviteActive.value = true
        scope.launch {
            delay(INVITE_TTL_MS)
            if (myInvite?.token === token) { myInvite = null; _inviteActive.value = false }
        }
        return PairInvite.encode(PairInvite.Invite(localDeviceId, token, _localName.value))
    }

    fun cancelInvite() {
        myInvite = null
        _inviteActive.value = false
    }

    /** Called with the text of a scanned QR. Connects automatically as soon as that phone is seen. */
    fun joinInvite(raw: String): Boolean {
        val invite = PairInvite.decode(raw)
        if (invite == null) { _status.value = "That QR code is not a Meshline invite"; return false }
        if (invite.deviceId == localDeviceId) { _status.value = "That is this phone's own QR code"; return false }
        joinTarget = invite
        _status.value = "Looking for ${invite.name} nearby"
        endpointPrefixes.entries.firstOrNull { invite.deviceId.startsWith(it.value) }?.let {
            requestConnectionTo(it.key, manual = false, qr = true)
        }
        scope.launch {
            delay(JOIN_WAIT_MS)
            if (joinTarget === invite) {
                joinTarget = null
                _status.value = "Could not find ${invite.name} · keep their QR on screen and try again"
            }
        }
        return true
    }

    // ---------------------------------------------------------------- connection setup

    /** The visible name carries the first 32 hex chars of our ID so paired phones can recognise us. */
    private fun advertisedName(): String =
        TextRules.truncateUtf8(_localName.value, MAX_ADVERTISED_NAME_BYTES) + NAME_SEP + localDeviceId.take(ID_PREFIX_LEN)

    private fun parseEndpointName(raw: String): ParsedName {
        var text = raw
        var qr = false
        if (text.endsWith("$NAME_SEP$QR_HINT")) { qr = true; text = text.dropLast(2) }
        val i = text.lastIndexOf(NAME_SEP)
        if (i < 0) return ParsedName(TextRules.sanitizeName(text), null, qr)
        val prefix = text.substring(i + 1)
        val valid = prefix.length == ID_PREFIX_LEN && prefix.all { it in '0'..'9' || it in 'a'..'f' }
        return ParsedName(TextRules.sanitizeName(text.substring(0, i)), if (valid) prefix else null, qr)
    }

    private fun requestConnectionTo(endpointId: String, manual: Boolean, qr: Boolean = false) {
        if (!requestedEndpoints.add(endpointId) && !manual) return
        _connecting.value = requestedEndpoints.toSet()
        val name = endpointNames[endpointId] ?: "Nearby device"
        val myName = advertisedName() + (if (qr) "$NAME_SEP$QR_HINT" else "")
        client.requestConnection(myName, endpointId, lifecycleCallback)
            .addOnFailureListener {
                dropRequest(endpointId)
                if (manual) _status.value = "Could not request connection: ${it.message ?: "try again"}"
            }
        _status.value = if (manual || qr) "Connecting to $name" else "Reconnecting to $name"
    }

    private fun scheduleAutoStop(afterMs: Long) {
        autoStopJob?.cancel()
        if (appVisible) return
        autoStopJob = scope.launch {
            delay(afterMs)
            if (_isDiscovering.value) {
                stopDiscovery()
                _status.value = "Search paused to save battery · open Meshline to resume"
            }
        }
    }

    // ---------------------------------------------------------------- handshake

    private fun beginSecureSession(endpointId: String) {
        _availablePeers.update { list -> list.filterNot { it.endpointId == endpointId } }
        try {
            val pair = SessionCrypto.newKeyPair()
            val early = synchronized(handshakeLock) {
                ephemeralKeys[endpointId] = pair
                earlyPackets.remove(endpointId) ?: emptyList<ByteArray>()
            }
            val hello = JSONObject()
                .put("type", "hello")
                .put("idKey", b64(identity.publicKeyEncoded))
                .put("name", _localName.value)
            joinTarget?.let { t ->
                if (expectedPeerByEndpoint[endpointId] == t.deviceId) {
                    hello.put("pair", PairInvite.proof(t.token, localDeviceId, t.deviceId))
                }
            }
            sendPlain(endpointId, hello)
            // Anything the peer sent before our side was ready is processed now, in order.
            early.forEach { inbox.trySend(endpointId to it) }
        } catch (e: Exception) {
            _status.value = "Could not initialise encryption: ${e.message ?: "security error"}"
            client.disconnectFromEndpoint(endpointId)
            cleanupEndpoint(endpointId)
        }
    }

    private fun sendPlain(endpointId: String, json: JSONObject) {
        val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
            .addOnFailureListener { _status.value = "Session setup failed: ${it.message ?: "radio error"}" }
    }

    private suspend fun handlePacket(endpointId: String, bytes: ByteArray) {
        try {
            val outer = JSONObject(String(bytes, StandardCharsets.UTF_8))
            val ready = synchronized(handshakeLock) {
                if (ephemeralKeys.containsKey(endpointId)) {
                    true
                } else {
                    val list = earlyPackets.getOrPut(endpointId) { mutableListOf() }
                    if (list.size < 8) list.add(bytes)
                    false
                }
            }
            if (!ready) return
            when (outer.optString("type")) {
                "hello" -> onHello(endpointId, outer)
                "key" -> onKey(endpointId, outer)
                "secure" -> handleSecurePacket(endpointId, outer)
                else -> _status.value = "Ignored an unknown packet"
            }
        } catch (_: Exception) {
            // Invalid, unauthenticated or malformed packets are discarded rather than crashing the app.
            _status.value = "A packet failed verification and was discarded"
        }
    }

    /** Step 1: learn who the peer claims to be. The ID is derived from its public key, never trusted as text. */
    private suspend fun onHello(endpointId: String, outer: JSONObject) {
        if (peerIdByEndpoint.containsKey(endpointId)) return
        val keyBytes = Base64.getDecoder().decode(outer.getString("idKey"))
        if (keyBytes.size !in 80..100) { dropEndpoint(endpointId, "Peer sent an invalid identity"); return }
        val peerId = Identity.idFromPublicKey(keyBytes)
        val expected = expectedPeerByEndpoint[endpointId]
        if (peerId == localDeviceId || (expected != null && expected != peerId)) {
            dropEndpoint(endpointId, "Device identity did not match a paired contact · disconnected")
            return
        }
        if (qrProvisional.remove(endpointId)) {
            val invite = myInvite
            val proof = outer.optString("pair")
            val ok = invite != null && invite.expiresAt > System.currentTimeMillis() && proof.isNotEmpty() &&
                PairInvite.verifyProof(invite.token, peerId, localDeviceId, proof)
            if (!ok) { dropEndpoint(endpointId, "QR pairing could not be verified · disconnected"); return }
            myInvite = null   // single use
            _inviteActive.value = false
        }
        peerIdByEndpoint[endpointId] = peerId
        peerIdentityKeys[endpointId] = keyBytes
        peerDisplay[endpointId] = TextRules.sanitizeName(outer.optString("name"), endpointNames[endpointId] ?: "Nearby device")
        sendKey(endpointId, peerId)
        pendingKeyPackets.remove(endpointId)?.let { onKey(endpointId, it) }
    }

    /** Step 2: send our ephemeral key, signed with our long-term key and bound to this specific peer. */
    private fun sendKey(endpointId: String, peerId: String) {
        val pair = ephemeralKeys[endpointId] ?: return
        val ephemeral = SessionCrypto.encodePublicKey(pair)
        val signature = identity.sign(Identity.signaturePayload(localDeviceId, peerId, ephemeral))
        sendPlain(endpointId, JSONObject()
            .put("type", "key")
            .put("publicKey", ephemeral)
            .put("sig", b64(signature)))
    }

    /** Step 3: verify the peer's signature, derive the session key, then go online. */
    private suspend fun onKey(endpointId: String, outer: JSONObject) {
        val peerId = peerIdByEndpoint[endpointId]
        if (peerId == null) { pendingKeyPackets[endpointId] = outer; return }
        if (sessionKeys.containsKey(endpointId)) return
        val pair = ephemeralKeys[endpointId] ?: return
        val peerKey = peerIdentityKeys[endpointId] ?: return
        val ephemeral = outer.getString("publicKey")
        val signature = Base64.getDecoder().decode(outer.getString("sig"))
        val payload = Identity.signaturePayload(peerId, localDeviceId, ephemeral)
        if (!Identity.verify(peerKey, payload, signature)) {
            dropEndpoint(endpointId, "Could not verify the device identity · disconnected")
            return
        }
        val sessionKey = SessionCrypto.deriveSessionKey(pair, ephemeral, localDeviceId, peerId)
        registerPeer(endpointId, peerId, peerDisplay[endpointId] ?: "Nearby device")
        if (joinTarget?.deviceId == peerId) joinTarget = null
        sessionKeys[endpointId] = sessionKey
        _status.value = "Secure peer-to-peer session ready"
        scheduleAutoStop(CONNECTED_STOP_MS)
        flushOutbox(peerId, endpointId)
    }

    private suspend fun registerPeer(endpointId: String, peerId: String, name: String) {
        val previous = endpointByPeerId.put(peerId, endpointId)
        if (previous != null && previous != endpointId) {
            // A fresh verified connection replaces an older one from the same device.
            client.disconnectFromEndpoint(previous)
            cleanupEndpoint(previous)
        }
        db.upsertPeer(peerId, name)
        _onlinePeerIds.value = endpointByPeerId.keys.toSet()
        refreshConversations()
        if (_selectedPeerId.value == peerId) refreshMessages(peerId)
    }

    private fun dropEndpoint(endpointId: String, reason: String) {
        client.disconnectFromEndpoint(endpointId)
        cleanupEndpoint(endpointId)
        _status.value = reason
    }

    // ---------------------------------------------------------------- encrypted messages

    private suspend fun handleSecurePacket(endpointId: String, outer: JSONObject) {
        val key = sessionKeys[endpointId] ?: return
        val peerId = peerIdByEndpoint[endpointId] ?: return
        if (outer.optString("from") != peerId) return
        val plain = SessionCrypto.decrypt(
            outer.getString("iv"), outer.getString("data"), key, outer.getString("packetId")
        )
        val inner = JSONObject(plain)
        when (inner.getString("kind")) {
            "message" -> {
                val messageId = inner.getString("id")
                val body = TextRules.sanitizeBody(inner.getString("body"))
                if (messageId.isBlank() || messageId.length > 80 || !TextRules.fits(body)) return
                val inserted = db.insertMessage(StoredMessage(
                    id = messageId, peerId = peerId, senderId = peerId, body = body,
                    // Local receive time: a peer's clock must not be able to reorder our history.
                    sentAt = System.currentTimeMillis(),
                    status = "DELIVERED", outgoing = false
                ))
                // ACK duplicates too: this repairs a lost ACK without duplicating the message.
                sendSecure(endpointId, JSONObject().put("kind", "ack").put("messageId", messageId))
                if (appVisible && _selectedPeerId.value == peerId) markRead(peerId)
                refreshConversations()
                if (_selectedPeerId.value == peerId) refreshMessages(peerId)
                if (inserted && !(appVisible && _selectedPeerId.value == peerId)) {
                    MessageNotifier.show(appContext, peerId, peerDisplay[endpointId] ?: "Nearby device", body)
                }
            }
            "ack" -> {
                val messageId = inner.getString("messageId")
                db.updateStatus(messageId, peerId, "DELIVERED")
                retryJobs.remove(messageId)?.cancel()
                refreshConversations()
                if (_selectedPeerId.value == peerId) refreshMessages(peerId)
            }
        }
    }

    private suspend fun flushOutbox(peerId: String, endpointId: String) {
        db.undelivered(peerId).forEach { sendStored(it, endpointId) }
    }

    private fun sendStored(message: StoredMessage, endpointId: String) {
        ensureRetryLoop(message)
        transmitStored(message, endpointId)
    }

    private fun transmitStored(message: StoredMessage, endpointId: String) {
        val payload = JSONObject()
            .put("kind", "message")
            .put("id", message.id)
            .put("body", message.body)
            .put("sentAt", message.sentAt)
        sendSecure(endpointId, payload)
            .addOnSuccessListener {
                scope.launch {
                    // updateStatus never downgrades DELIVERED if a very fast ACK wins this race.
                    db.updateStatus(message.id, message.peerId, "SENT")
                    refreshMessages(message.peerId)
                    refreshConversations()
                }
            }
            .addOnFailureListener { error ->
                scope.launch {
                    if (error is PacketTooLargeException) {
                        // Retrying can never succeed: stop instead of looping forever.
                        db.updateStatus(message.id, message.peerId, "FAILED")
                        _status.value = "Message is too large to send"
                    } else {
                        db.updateStatus(message.id, message.peerId, "PENDING")
                        _status.value = "Send interrupted · message safely queued for retry"
                    }
                    refreshMessages(message.peerId)
                }
            }
    }

    private fun ensureRetryLoop(message: StoredMessage) {
        synchronized(retryLock) {
            if (retryJobs[message.id]?.isActive == true) return
            retryJobs[message.id] = scope.launch {
                val self = coroutineContext[Job]
                try {
                    // Retransmit until ACK, with capped backoff. Receiver deduplication makes retries safe.
                    var waitMs = 2_000L
                    while (true) {
                        delay(waitMs)
                        val endpoint = endpointByPeerId[message.peerId]
                        // Peer gone: stop. flushOutbox() restarts the loop when it reconnects.
                        if (endpoint == null || !sessionKeys.containsKey(endpoint)) break
                        if (!db.isOutgoingUndelivered(message.id)) break
                        transmitStored(message, endpoint)
                        waitMs = (waitMs * 2).coerceAtMost(60_000L)
                    }
                } finally {
                    if (self != null) retryJobs.remove(message.id, self)
                }
            }
        }
    }

    private fun sendSecure(endpointId: String, inner: JSONObject): Task<Void> {
        val key = sessionKeys[endpointId]
            ?: return Tasks.forException<Void>(IllegalStateException("Secure session is not ready"))
        val packetId = UUID.randomUUID().toString()
        val (iv, encrypted) = SessionCrypto.encrypt(inner.toString(), key, packetId)
        val packet = JSONObject()
            .put("type", "secure")
            .put("from", localDeviceId)
            .put("packetId", packetId)
            .put("iv", iv)
            .put("data", encrypted)
        val bytes = packet.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > Limits.MAX_PACKET_BYTES) return Tasks.forException<Void>(PacketTooLargeException())
        return client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    // ---------------------------------------------------------------- storage refresh / cleanup

    private suspend fun refreshMessages(peerId: String) {
        val loaded = db.messages(peerId)
        // A slower read for a previously selected thread must not replace the current one.
        if (_selectedPeerId.value == peerId) _messages.value = loaded
    }

    private suspend fun refreshConversations() {
        val list = db.conversations()
        _conversations.value = list
        _unread.value = list.associate { it.peerId to db.unreadCount(it.peerId, lastRead(it.peerId)) }
    }

    private fun cleanupEndpoint(endpointId: String) {
        val peerId = peerIdByEndpoint.remove(endpointId)
        if (peerId != null) endpointByPeerId.remove(peerId, endpointId)
        peerIdentityKeys.remove(endpointId)
        expectedPeerByEndpoint.remove(endpointId)
        qrProvisional.remove(endpointId)
        pendingKeyPackets.remove(endpointId)
        peerDisplay.remove(endpointId)
        dropRequest(endpointId)
        synchronized(handshakeLock) {
            ephemeralKeys.remove(endpointId)
            earlyPackets.remove(endpointId)
        }
        sessionKeys.remove(endpointId)
        _onlinePeerIds.value = endpointByPeerId.keys.toSet()
        _pendingPairings.update { list -> list.filterNot { it.endpointId == endpointId } }
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun acceptSilently(endpointId: String) {
        client.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { _status.value = "Could not accept connection: ${it.message ?: "try again"}" }
    }

    private fun dropRequest(id: String) {
        requestedEndpoints.remove(id)
        _connecting.value = requestedEndpoints.toSet()
    }

    private fun clearRequests() {
        requestedEndpoints.clear()
        endpointPrefixes.clear()
        _connecting.value = emptySet()
    }

    private fun lastRead(peerId: String): Long = prefs.getLong("read_$peerId", 0L)
    private fun markRead(peerId: String) { prefs.edit().putLong("read_$peerId", System.currentTimeMillis()).apply() }
}
