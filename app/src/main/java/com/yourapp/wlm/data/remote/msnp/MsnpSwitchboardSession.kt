package com.yourapp.wlm.data.remote.msnp

import android.util.Log
import com.yourapp.wlm.data.remote.msnp.MsnpCommandParser.MessagePayload
import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.model.MessageType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MsnpSwitchboardSession(
    private val host: String,
    private val port: Int,
    private val authToken: String,
    private val userEmail: String,
    private val contactEmail: String,
    private val sessionId: String = ""
) {
    companion object {
        private const val TAG = "WLM_SBSession"
    }

    private var connection: MsnpConnection? = null
    private var handler: MsnpSbHandler? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var isConnected = false
        private set

    val messageFlow: Flow<Message> = callbackFlow {
        val handlerRef = handler
        if (handlerRef != null) {
            for (event in handlerRef.eventChannel) {
                when (event) {
                    is MsnpSbHandler.SbEvent.MessageReceived -> trySend(event.message)
                    else -> {}
                }
            }
        }
        awaitClose { }
    }

    suspend fun connect(): Result<Unit> {
        return try {
            Log.d(TAG, "Connecting to SB at $host:$port")
            connection = MsnpConnection(host, port)
            val connectResult = connection!!.connect()
            if (connectResult.isFailure) {
                return connectResult
            }

            handler = MsnpSbHandler(connection!!, userEmail, contactEmail)
            handler!!.start()

            val trid1 = connection!!.nextTransactionId()
            connection!!.sendCommand(MsnpCommandBuilder.sbUsrs(trid1, userEmail, authToken))
                .getOrElse { return Result.failure(it) }

            delay(500)

            if (sessionId.isBlank()) {
                val trid2 = connection!!.nextTransactionId()
                connection!!.sendCommand(MsnpCommandBuilder.sbCal(trid2, contactEmail))
                    .getOrElse { return Result.failure(it) }
            } else {
                val trid2 = connection!!.nextTransactionId()
                connection!!.sendCommand(MsnpCommandBuilder.sbAns(trid2, userEmail, authToken, sessionId))
                    .getOrElse { return Result.failure(it) }
            }

            isConnected = true
            Log.d(TAG, "SB session established with $contactEmail")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect SB", e)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(body: String): Result<Unit> {
        return try {
            if (!isConnected || connection == null) {
                return Result.failure(IllegalStateException("SB not connected"))
            }
            val msgBody = MsnpCommandBuilder.msgBuildBody(body)
            val contentLength = msgBody.toByteArray().size
            val trid = connection!!.nextTransactionId()
            val cmd = MsnpCommandBuilder.msgSend(trid, contentLength)
            connection!!.sendCommandWithBody(cmd, msgBody)
                .getOrElse { return Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            Result.failure(e)
        }
    }

    suspend fun sendTyping(): Result<Unit> {
        return try {
            if (!isConnected || connection == null) {
                return Result.failure(IllegalStateException("SB not connected"))
            }
            val msgBody = MsnpCommandBuilder.msgBuildBody("", isControl = true, typingUser = userEmail)
            val contentLength = msgBody.toByteArray().size
            val trid = connection!!.nextTransactionId()
            val cmd = MsnpCommandBuilder.msgSend(trid, contentLength, isControl = true)
            connection!!.sendCommandWithBody(cmd, msgBody)
                .getOrElse { return Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send typing", e)
            Result.failure(e)
        }
    }

    suspend fun sendNudge(): Result<Unit> {
        return try {
            if (!isConnected || connection == null) {
                return Result.failure(IllegalStateException("SB not connected"))
            }
            val msgBody = MsnpCommandBuilder.nudgeBuild()
            val contentLength = msgBody.toByteArray().size
            val trid = connection!!.nextTransactionId()
            val cmd = MsnpCommandBuilder.msgSend(trid, contentLength)
            connection!!.sendCommandWithBody(cmd, msgBody)
                .getOrElse { return Result.failure(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send nudge", e)
            Result.failure(e)
        }
    }

    suspend fun disconnect() {
        try {
            isConnected = false
            connection?.disconnect()
            connection = null
            handler = null
            scope.cancel()
            Log.d(TAG, "SB session disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error during SB disconnect", e)
        }
    }
}

class MsnpSbHandler(
    private val connection: MsnpConnection,
    private val userEmail: String,
    private val contactEmail: String
) {
    companion object {
        private const val TAG = "WLM_SbHandler"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _eventChannel = Channel<SbEvent>(Channel.UNLIMITED)
    val eventChannel = _eventChannel

    sealed class SbEvent {
        data class MessageReceived(val message: Message) : SbEvent()
        data class TypingNotification(val email: String) : SbEvent()
        data class NudgeReceived(val email: String) : SbEvent()
        data class ContactJoined(val email: String, val displayName: String) : SbEvent()
        object Disconnected : SbEvent()
    }

    fun start() {
        scope.launch {
            var pendingContentLength = 0
            var isReadingBody = false
            var bodyBuffer = StringBuilder()
            var lastParsedCommand: MsnpCommandParser.ParsedCommand? = null

            for (line in connection.commandChannel) {
                if (isReadingBody && pendingContentLength > 0) {
                    bodyBuffer.append(line).append("\n")
                    if (bodyBuffer.length >= pendingContentLength) {
                        val body = bodyBuffer.toString().take(pendingContentLength)
                        isReadingBody = false
                        pendingContentLength = 0

                        lastParsedCommand?.let { parsed ->
                            processMessage(parsed, body)
                        }
                        bodyBuffer = StringBuilder()
                    }
                    continue
                }

                val parsed = MsnpCommandParser.parse(line)

                when (parsed.command) {
                    "MSG" -> {
                        val contentLength = parsed.parts.getOrNull(4)?.toIntOrNull() ?: 0
                        if (contentLength > 0) {
                            pendingContentLength = contentLength
                            isReadingBody = true
                            lastParsedCommand = parsed
                            bodyBuffer = StringBuilder()
                        } else {
                            processMessage(parsed, "")
                        }
                    }
                    "JOI" -> {
                        val email = parsed.parts.getOrNull(0) ?: ""
                        val displayName = parsed.parts.getOrNull(1) ?: ""
                        scope.launch {
                            _eventChannel.send(SbEvent.ContactJoined(email, displayName))
                        }
                    }
                    "215" -> {
                        Log.d(TAG, "SB: Auth OK")
                    }
                    "280" -> {
                        Log.d(TAG, "SB: CAL RINGING")
                    }
                    "216" -> {
                        Log.d(TAG, "SB: ANS OK")
                    }
                    "302" -> {
                        Log.d(TAG, "SB: Auth required")
                    }
                    in "100".."999" -> {
                        val code = parsed.command.toIntOrNull() ?: 0
                        val msg = parsed.parts.joinToString(" ")
                        if (code >= 400) {
                            Log.e(TAG, "SB Error $code: $msg")
                        }
                    }
                    else -> {
                        Log.d(TAG, "SB unhandled: $line")
                    }
                }
            }
        }
    }

    private fun processMessage(parsed: MsnpCommandParser.ParsedCommand, body: String) {
        val payload = MsnpCommandParser.parseMessage(
            "${parsed.command} ${parsed.parts.joinToString(" ")}",
            body
        )

        scope.launch {
            when {
                payload.isNudge -> {
                    _eventChannel.send(SbEvent.NudgeReceived(payload.senderEmail))
                }
                payload.isTyping -> {
                    _eventChannel.send(SbEvent.TypingNotification(payload.senderEmail))
                }
                else -> {
                    val message = Message(
                        conversationId = payload.senderEmail,
                        senderEmail = payload.senderEmail,
                        senderDisplayName = payload.senderDisplayName,
                        body = payload.body,
                        timestamp = System.currentTimeMillis(),
                        isOutgoing = false,
                        messageType = payload.messageType
                    )
                    _eventChannel.send(SbEvent.MessageReceived(message))
                }
            }
        }
    }
}
