package org.nepalscouts.rescuer.ptt

import com.zello.sdk.Zello
import com.zello.sdk.ZelloChannel
import com.zello.sdk.ZelloConnectionError
import com.zello.sdk.ZelloCredentials
import com.zello.sdk.ZelloIncomingVoiceMessage
import com.zello.sdk.ZelloOutgoingVoiceMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PttRepository @Inject constructor(
    val zello: Zello
) : Zello.Listener {

    data class UiState(
        val started: Boolean = false,
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val transmitting: Boolean = false,
        val incoming: Boolean = false,
        val incomingFrom: String? = null,
        val selectedChannel: String? = null,
        val error: String? = null,
        val channels: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    init {
        zello.listener = this
    }

    fun start() {
        zello.start()
        _state.value = _state.value.copy(started = true)
    }

    fun connect(network: String, username: String, password: String) {
        _state.value = _state.value.copy(connecting = true, error = null)
        zello.connect(ZelloCredentials(network, username, password))
    }

    fun disconnect() {
        zello.disconnect()
    }

    fun findChannel(name: String): ZelloChannel? =
        zello.channels.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun selectChannel(name: String): Boolean {
        val channel = findChannel(name) ?: run {
            _state.value = _state.value.copy(error = "PTT channel not available: $name")
            return false
        }
        zello.connectChannel(channel)
        zello.setSelectedContact(channel)
        _state.value = _state.value.copy(selectedChannel = channel.name, error = null)
        return true
    }

    fun pressToTalk(channelName: String): Boolean {
        val channel = findChannel(channelName) ?: run {
            _state.value = _state.value.copy(error = "PTT channel not available: $channelName")
            return false
        }
        zello.connectChannel(channel)
        zello.setSelectedContact(channel)
        zello.stopVoiceMessage()
        zello.startVoiceMessage(channel)
        _state.value = _state.value.copy(selectedChannel = channel.name, transmitting = true, error = null)
        return true
    }

    fun releaseToStop() {
        zello.stopVoiceMessage()
        _state.value = _state.value.copy(transmitting = false)
    }

    override fun onConnectStarted(zello: Zello) {
        _state.value = _state.value.copy(connecting = true, error = null)
    }

    override fun onConnectSucceeded(zello: Zello) {
        _state.value = _state.value.copy(
            connected = true,
            connecting = false,
            channels = zello.channels.map { it.name },
            error = null
        )
    }

    override fun onConnectFailed(zello: Zello, error: ZelloConnectionError) {
        _state.value = _state.value.copy(connected = false, connecting = false, error = "Zello connection failed: ${error.name}")
    }

    override fun onDisconnected(zello: Zello, reconnecting: Boolean) {
        _state.value = _state.value.copy(connected = false, connecting = reconnecting, transmitting = false)
    }

    override fun onContactListUpdated(zello: Zello) {
        _state.value = _state.value.copy(channels = zello.channels.map { it.name })
    }

    override fun onIncomingVoiceMessageStarted(zello: Zello, message: ZelloIncomingVoiceMessage) {
        _state.value = _state.value.copy(
            incoming = true,
            incomingFrom = message.contact.name,
            selectedChannel = message.contact.name
        )
    }

    override fun onIncomingVoiceMessageStopped(zello: Zello, message: ZelloIncomingVoiceMessage) {
        _state.value = _state.value.copy(incoming = false, incomingFrom = null)
    }

    override fun onOutgoingVoiceMessageConnecting(zello: Zello, message: ZelloOutgoingVoiceMessage) {
        _state.value = _state.value.copy(transmitting = true, selectedChannel = message.contact.name)
    }

    override fun onOutgoingVoiceMessageStarted(zello: Zello, message: ZelloOutgoingVoiceMessage) {
        _state.value = _state.value.copy(transmitting = true, selectedChannel = message.contact.name)
    }

    override fun onOutgoingVoiceMessageStopped(
        zello: Zello,
        message: ZelloOutgoingVoiceMessage,
        error: ZelloOutgoingVoiceMessage.Error?
    ) {
        _state.value = _state.value.copy(
            transmitting = false,
            error = error?.let { "Voice transmission failed: ${it.name}" }
        )
    }
}
