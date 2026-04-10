package com.yourapp.wlm.data.remote.msnp

import android.util.Log
import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.model.MessageType
import com.yourapp.wlm.domain.model.PresenceStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

class MsnpNotificationHandler(
    private val connection: MsnpConnection,
    private val email: String,
    private val passportTicket: String
) {
    companion object {
        private const val TAG = "WLM_NsHandler"
        private const val PING_INTERVAL = 45L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null

    private val _connectionState = MutableStateFlow(MsnpNotificationState.DISCONNECTED)
    val connectionState: StateFlow<MsnpNotificationState> = _connectionState.asStateFlow()

    sealed class MsnpNotificationState {
        object Disconnected : MsnpNotificationState()
        object Connecting : MsnpNotificationState()
        object Connected : MsnpNotificationState()
        data class Error(val message: String) : MsnpNotificationState()
        data class ServerError(val code: Int, val message: String) : MsnpNotificationState()
    }

    sealed class NsEvent {
        data class ContactPresence(val email: String, val status: PresenceStatus, val displayName: String, val capabilities: Long) : NsEvent()
        data class PersonalMessageChanged(val email: String, val pm: String) : NsEvent()
        data class MessageReceived(val message: Message) : NsEvent()
        data class TypingNotification(val email: String) : NsEvent()
        data class NudgeReceived(val email: String) : NsEvent()
        data class SwitchboardInvitation(val sessionId: String, val host: String, val port: Int, val authToken: String) : NsEvent()
        data class RingInvitation(val sessionId: String, val host: String, val port: Int, val authToken: String, val callerEmail: String, val callerDisplayName: String) : NsEvent()
        data class Notification(val message: String) : NsEvent()
        object Disconnected : NsEvent()
        data class ReconnectRequired(val reason: String) : NsEvent()
    }

    private val _eventChannel = Channel<NsEvent>(Channel.UNLIMITED)
    val eventChannel = _eventChannel

    fun start() {
        scope.launch {
            for (line in connection.commandChannel) {
                processCommand(line)
            }
        }
    }

    private fun processCommand(line: String) {
        val parsed = MsnpCommandParser.parse(line)
        Log.d(TAG, "Processing: ${parsed.command}")

        when (parsed.command) {
            "VER" -> handleVer(parsed)
            "CVR" -> handleCvr(parsed)
            "USR" -> handleUsr(parsed)
            "GCF" -> handleGcf(parsed)
            "NLN", "FLN", "ILN" -> handlePresence(parsed)
            "MSG" -> handleMessage(parsed)
            "XFR" -> handleXfr(parsed)
            "RNG" -> handleRng(parsed)
            "ADL", "RML" -> handleListChange(parsed)
            "UBX" -> handleUbx(parsed)
            "QNG" -> handleQng(parsed)
            "NOT" -> handleNot(parsed)
            "OUT" -> handleOut(parsed)
            "CHG" -> handleChg(parsed)
            "UBN" -> handleUbn(parsed)
            in "100".."999" -> handleError(parsed)
            else -> Log.w(TAG, "Unhandled command: ${parsed.command}")
        }
    }

    private fun handleVer(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "VER: Server supports ${parsed.parts.joinToString(" ")}")
    }

    private fun handleCvr(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "CVR: Server version info received")
    }

    private fun handleUsr(parsed: MsnpCommandParser.ParsedCommand) {
        val userInfo = MsnpCommandParser.parseUsrOk(parsed)
        if (userInfo != null) {
            Log.d(TAG, "USR OK: ${userInfo.displayName} (${userInfo.email})")
            _connectionState.value = MsnpNotificationState.Connected
            startPingTimer()
        }
    }

    private fun handleGcf(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "GCF: Config received")
    }

    private fun handlePresence(parsed: MsnpCommandParser.ParsedCommand) {
        val presenceInfo = MsnpCommandParser.parsePresenceStatus(parsed)
        if (presenceInfo != null) {
            Log.d(TAG, "Presence: ${presenceInfo.email} -> ${presenceInfo.status}")
            scope.launch {
                _eventChannel.send(NsEvent.ContactPresence(
                    email = presenceInfo.email,
                    status = presenceInfo.status,
                    displayName = presenceInfo.displayName,
                    capabilities = presenceInfo.capabilities
                ))
            }
        }
    }

    private fun handleMessage(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "MSG: Message received")
    }

    private fun handleXfr(parsed: MsnpCommandParser.ParsedCommand) {
        val sbInfo = MsnpCommandParser.parseXfrResponse(parsed)
        if (sbInfo != null) {
            Log.d(TAG, "XFR SB: ${sbInfo.host}:${sbInfo.port}")
            scope.launch {
                _eventChannel.send(NsEvent.SwitchboardInvitation(
                    sessionId = "",
                    host = sbInfo.host,
                    port = sbInfo.port,
                    authToken = sbInfo.authToken
                ))
            }
        }
    }

    private fun handleRng(parsed: MsnpCommandParser.ParsedCommand) {
        val rngInfo = MsnpCommandParser.parseRngInvitation(parsed)
        if (rngInfo != null) {
            Log.d(TAG, "RNG: ${rngInfo.callerEmail} inviting to SB")
            scope.launch {
                _eventChannel.send(NsEvent.RingInvitation(
                    sessionId = rngInfo.sessionId,
                    host = rngInfo.host,
                    port = rngInfo.port,
                    authToken = rngInfo.authToken,
                    callerEmail = rngInfo.callerEmail,
                    callerDisplayName = rngInfo.callerDisplayName
                ))
            }
        }
    }

    private fun handleListChange(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "List change: ${parsed.command}")
    }

    private fun handleUbx(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "UBX: Personal message update received")
    }

    private fun handleQng(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "QNG: Pong received")
    }

    private fun handleNot(parsed: MsnpCommandParser.ParsedCommand) {
        val message = parsed.parts.joinToString(" ")
        Log.d(TAG, "NOT: $message")
        scope.launch {
            _eventChannel.send(NsEvent.Notification(message))
        }
    }

    private fun handleOut(parsed: MsnpCommandParser.ParsedCommand) {
        Log.w(TAG, "OUT: Server requested disconnect")
        stopPingTimer()
        scope.launch {
            _eventChannel.send(NsEvent.ReconnectRequired("Server requested disconnect"))
            _eventChannel.send(NsEvent.Disconnected)
        }
    }

    private fun handleChg(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "CHG: Status change confirmed")
    }

    private fun handleUbn(parsed: MsnpCommandParser.ParsedCommand) {
        Log.d(TAG, "UBN: Notification received")
    }

    private fun handleError(parsed: MsnpCommandParser.ParsedCommand) {
        val code = parsed.command.toIntOrNull() ?: 0
        val message = parsed.parts.joinToString(" ")
        Log.e(TAG, "Server error $code: $message")

        scope.launch {
            _eventChannel.send(NsEvent.ServerError(code, message))

            if (code == 911) {
                _connectionState.value = MsnpNotificationState.Error("Authentication failed")
                _eventChannel.send(NsEvent.ReconnectRequired("Authentication failed (911)"))
            }
        }
    }

    private fun startPingTimer() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(PING_INTERVAL.seconds)
                if (connection.isConnected) {
                    connection.sendCommand(MsnpCommandBuilder.ping())
                        .onFailure { Log.e(TAG, "Failed to send ping", it) }
                } else {
                    break
                }
            }
        }
    }

    private fun stopPingTimer() {
        pingJob?.cancel()
        pingJob = null
    }

    suspend fun performHandshake(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = MsnpNotificationState.Connecting

            val trid1 = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.ver(trid1))
                .getOrElse { return@withContext Result.failure(it) }

            delay(500)

            val trid2 = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.cvr(trid2, email))
                .getOrElse { return@withContext Result.failure(it) }

            delay(500)

            val trid3 = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.usrInitial(trid3, email))
                .getOrElse { return@withContext Result.failure(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendTicketToServer(ticket: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.usrWithTicket(trid, email, ticket))
                .getOrElse { return@withContext Result.failure(it) }

            delay(1000)

            val trid2 = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.syn(trid2, 0, 0))
                .getOrElse { return@withContext Result.failure(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ticket", e)
            Result.failure(e)
        }
    }

    suspend fun changePresence(status: PresenceStatus, capabilities: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.chg(trid, status.msnpCode, capabilities))
                .getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change presence", e)
            Result.failure(e)
        }
    }

    suspend fun setPersonalMessage(message: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            val cmd = MsnpCommandBuilder.uux(trid, message)
            val xmlBody = "<Data><PSM>${escapeXml(message)}</PSM></Data>"
            connection.sendCommandWithBody(cmd, xmlBody)
                .getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set personal message", e)
            Result.failure(e)
        }
    }

    suspend fun requestSwitchboard(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            connection.sendCommand(MsnpCommandBuilder.xfrSb(trid))
                .getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request SB", e)
            Result.failure(e)
        }
    }

    suspend fun addContact(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            val cmd = MsnpCommandBuilder.adlAdd(trid, email)
            val xmlBody = "<ml><d n=\"${email.split("@").getOrNull(1) ?: "msn.com"}\"><c n=\"${email.split("@").getOrNull(0) ?: email}\" l=\"1\" t=\"1\"/></d></ml>"
            connection.sendCommandWithBody(cmd, xmlBody)
                .getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add contact", e)
            Result.failure(e)
        }
    }

    suspend fun removeContact(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val trid = connection.nextTransactionId()
            val cmd = MsnpCommandBuilder.rmlRemove(trid, email)
            val xmlBody = "<ml><d n=\"${email.split("@").getOrNull(1) ?: "msn.com"}\"><c n=\"${email.split("@").getOrNull(0) ?: email}\" l=\"1\" t=\"1\"/></d></ml>"
            connection.sendCommandWithBody(cmd, xmlBody)
                .getOrElse { return@withContext Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove contact", e)
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stopPingTimer()
            connection.sendCommand(MsnpCommandBuilder.out())
            connection.disconnect()
            _connectionState.value = MsnpNotificationState.Disconnected
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
            Result.failure(e)
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
