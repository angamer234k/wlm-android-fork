package com.yourapp.wlm.data.remote.msnp

import android.util.Log
import com.yourapp.wlm.domain.model.MessageType
import com.yourapp.wlm.domain.model.PresenceStatus

object MsnpCommandParser {
    private const val TAG = "WLM_CommandParser"

    data class ParsedCommand(
        val command: String,
        val trid: Int? = null,
        val parts: List<String> = emptyList(),
        val raw: String = ""
    )

    data class MessagePayload(
        val senderEmail: String = "",
        val senderDisplayName: String = "",
        val body: String = "",
        val contentType: String = "text/plain",
        val isTyping: Boolean = false,
        val isNudge: Boolean = false,
        val messageType: MessageType = MessageType.TEXT
    )

    fun parse(line: String): ParsedCommand {
        if (line.isBlank()) return ParsedCommand(command = "", raw = line)

        val parts = line.split(" ")
        val command = parts[0]

        val trid = parts.getOrNull(1)?.toIntOrNull()

        return ParsedCommand(
            command = command,
            trid = trid,
            parts = if (trid != null) parts.drop(2) else parts.drop(1),
            raw = line
        )
    }

    fun parseMessage(line: String, body: String): MessagePayload {
        return try {
            val parts = line.split(" ")

            if (parts[0] == "MSG") {
                val senderEmail = parts.getOrNull(1) ?: ""
                val senderDisplayName = parts.getOrNull(2) ?: ""
                val contentLength = parts.getOrNull(4)?.toIntOrNull() ?: 0

                parseMessageBody(body, senderEmail, senderDisplayName)
            } else {
                MessagePayload()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
            MessagePayload()
        }
    }

    private fun parseMessageBody(body: String, senderEmail: String, senderDisplayName: String): MessagePayload {
        if (body.isBlank()) {
            return MessagePayload(
                senderEmail = senderEmail,
                senderDisplayName = senderDisplayName
            )
        }

        val headerBodySplit = body.indexOf("\r\n\r\n")
        val headers = if (headerBodySplit >= 0) body.substring(0, headerBodySplit) else ""
        val messageBody = if (headerBodySplit >= 0) body.substring(headerBodySplit + 4) else body

        val contentType = extractHeader(headers, "Content-Type:")
        val typingUser = extractHeader(headers, "TypingUser:")

        val isTyping = contentType.contains("text/x-msmsgscontrol", ignoreCase = true)
        val isNudge = contentType.contains("text/x-msnmsgr-datacast", ignoreCase = true)

        val messageType = when {
            isTyping -> MessageType.TEXT
            isNudge -> MessageType.NUDGE
            else -> MessageType.TEXT
        }

        return MessagePayload(
            senderEmail = senderEmail,
            senderDisplayName = senderDisplayName,
            body = messageBody.trim(),
            contentType = contentType,
            isTyping = isTyping,
            isNudge = isNudge,
            messageType = messageType
        )
    }

    fun parsePresenceStatus(parsed: ParsedCommand): PresenceInfo? {
        return when (parsed.command) {
            "NLN" -> {
                val capabilities = parsed.parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val email = parsed.parts.getOrNull(2) ?: return null
                val displayName = parsed.parts.getOrNull(3) ?: email
                PresenceInfo(
                    email = email,
                    status = PresenceStatus.ONLINE,
                    displayName = displayName,
                    capabilities = capabilities
                )
            }
            "FLN" -> {
                val email = parsed.parts.getOrNull(1) ?: return null
                PresenceInfo(email = email, status = PresenceStatus.OFFLINE)
            }
            "ILN" -> {
                val statusCode = parsed.parts.getOrNull(1) ?: return null
                val email = parsed.parts.getOrNull(2) ?: return null
                val displayName = parsed.parts.getOrNull(3) ?: email
                val capabilities = parsed.parts.getOrNull(4)?.toLongOrNull() ?: 0L
                PresenceInfo(
                    email = email,
                    status = PresenceStatus.fromMsnpCode(statusCode),
                    displayName = displayName,
                    capabilities = capabilities
                )
            }
            else -> null
        }
    }

    fun parseXfrResponse(parsed: ParsedCommand): SwitchboardInfo? {
        if (parsed.command != "XFR" || parsed.parts.size < 3) return null

        val hostPort = parsed.parts[1]
        val authKeyword = parsed.parts[2]
        if (authKeyword != "CKI") return null

        val authToken = parsed.parts.getOrNull(3) ?: return null

        val (host, port) = hostPort.split(":").let {
            it[0] to it.getOrNull(1)?.toIntOrNull() ?: 1863
        }

        return SwitchboardInfo(host, port, authToken)
    }

    fun parseRngInvitation(parsed: ParsedCommand): RngInfo? {
        if (parsed.command != "RNG" || parsed.parts.size < 5) return null

        val sessionId = parsed.parts[0]
        val hostPort = parsed.parts[1]
        val authKeyword = parsed.parts[2]
        if (authKeyword != "CKI") return null

        val authToken = parsed.parts[3]
        val callerEmail = parsed.parts[4]
        val callerDisplayName = parsed.parts.getOrNull(5) ?: callerEmail

        val (host, port) = hostPort.split(":").let {
            it[0] to it.getOrNull(1)?.toIntOrNull() ?: 1863
        }

        return RngInfo(sessionId, host, port, authToken, callerEmail, callerDisplayName)
    }

    fun parseUsrOk(parsed: ParsedCommand): UserInfo? {
        if (parsed.command != "USR" || parsed.parts.getOrNull(0) != "OK") return null

        val email = parsed.parts.getOrNull(1) ?: return null
        val displayName = parsed.parts.getOrNull(2) ?: email
        val verified = parsed.parts.getOrNull(3) == "1"

        return UserInfo(email, displayName, verified)
    }

    fun parseUbxPersonalMessage(body: String): String {
        val psmPattern = "<PSM>(.*?)</PSM>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matchResult = psmPattern.find(body)
        return matchResult?.groupValues?.getOrNull(1) ?: ""
    }

    private fun extractHeader(headers: String, headerName: String): String {
        val pattern = Regex("$headerName\\s*(.+?)(?:\\r\\n|$)", RegexOption.IGNORE_CASE)
        return pattern.find(headers)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    data class PresenceInfo(
        val email: String,
        val status: PresenceStatus,
        val displayName: String = "",
        val capabilities: Long = 0L
    )

    data class SwitchboardInfo(
        val host: String,
        val port: Int,
        val authToken: String
    )

    data class RngInfo(
        val sessionId: String,
        val host: String,
        val port: Int,
        val authToken: String,
        val callerEmail: String,
        val callerDisplayName: String
    )

    data class UserInfo(
        val email: String,
        val displayName: String,
        val verified: Boolean
    )
}
