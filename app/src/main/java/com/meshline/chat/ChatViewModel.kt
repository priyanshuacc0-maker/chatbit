package com.meshline.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    // Process-scoped on purpose: the connection must outlive the Activity and this ViewModel.
    private val repository = (application as MeshlineApplication).repository

    val status = repository.status
    val isDiscovering = repository.isDiscovering
    val availablePeers = repository.availablePeers
    val pendingPairings = repository.pendingPairings
    val conversations = repository.conversations
    val messages = repository.messages
    val selectedPeerId = repository.selectedPeerId
    val onlinePeerIds = repository.onlinePeerIds
    val localName = repository.localName
    val localDeviceId = repository.localDeviceId
    val unread = repository.unread
    val connecting = repository.connecting
    val inviteActive = repository.inviteActive
    val problem = repository.problem
    val searchPausedByUser: Boolean get() = repository.searchPausedByUser

    fun startNearby() = repository.startNearby()
    fun stopDiscovery() = repository.stopDiscovery(byUser = true)
    fun stopAll() = repository.stopAll()
    fun connect(endpointId: String) = repository.connect(endpointId)
    fun acceptPairing(endpointId: String) = repository.acceptPairing(endpointId)
    fun rejectPairing(endpointId: String) = repository.rejectPairing(endpointId)
    fun selectConversation(peerId: String) = repository.selectConversation(peerId)
    fun closeConversation() = repository.closeConversation()
    fun sendMessage(peerId: String, text: String) = repository.sendMessage(peerId, text)
    fun setDisplayName(name: String) = repository.setDisplayName(name)
    fun forgetPeer(peerId: String) = repository.forgetPeer(peerId)
    fun reportStatus(message: String) = repository.reportStatus(message)
    fun setAppVisible(visible: Boolean) = repository.setAppVisible(visible)

    fun createInvite(): String = repository.createInvite()
    fun cancelInvite() = repository.cancelInvite()
    fun joinInvite(raw: String): Boolean = repository.joinInvite(raw)

    fun permissionDenied() = repository.reportPermissionDenied()
}
