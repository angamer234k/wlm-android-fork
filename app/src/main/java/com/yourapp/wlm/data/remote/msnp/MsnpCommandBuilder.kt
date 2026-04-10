package com.yourapp.wlm.data.remote.msnp

import java.net.URLEncoder

object MsnpCommandBuilder {
    private const val MSNP_VERSION = "MSNP18"

    fun ver(trid: Int): String = "VER $trid $MSNP_VERSION MSNP15"

    fun cvr(trid: Int, email: String): String {
        val langId = "0x0409"
        val os = "winnt"
        val osVersion = "6.0"
        val arch = "i386"
        val clientName = "MSNMSGR"
        val clientVersion = "14.0.8117.0416"
        val clientName2 = "msmsgs"
        return "CVR $trid $langId $os $osVersion $arch $clientName $clientVersion $clientName2 $email"
    }

    fun usrInitial(trid: Int, email: String): String = "USR $trid TWN I $email"

    fun usrWithTicket(trid: Int, email: String, ticket: String): String = "USR $trid TWN S $ticket"

    fun syn(trid: Int, timestamp1: Long = 0, timestamp2: Long = 0): String = "SYN $trid $timestamp1 $timestamp2"

    fun chg(trid: Int, status: String, capabilities: Long = 0): String = "CHG $trid $status $capabilities"

    fun ping(): String = "PNG"

    fun uux(trid: Int, personalMessage: String): String {
        val xmlBody = "<Data><PSM>${escapeXml(personalMessage)}</PSM></Data>"
        val length = xmlBody.toByteArray().size
        return "UUX $trid $length"
    }

    fun xfrSb(trid: Int): String = "XFR $trid SB"

    fun blp(trid: Int, allowList: Boolean = true): String = "BLP $trid ${if (allowList) "AL" else "BL"}"

    fun out(): String = "OUT"

    fun adlAdd(trid: Int, email: String): String {
        val (user, domain) = splitEmail(email)
        val xmlBody = "<ml><d n=\"$domain\"><c n=\"$user\" l=\"1\" t=\"1\"/></d></ml>"
        val length = xmlBody.toByteArray().size
        return "ADL $trid $length"
    }

    fun rmlRemove(trid: Int, email: String): String {
        val (user, domain) = splitEmail(email)
        val xmlBody = "<ml><d n=\"$domain\"><c n=\"$user\" l=\"1\" t=\"1\"/></d></ml>"
        val length = xmlBody.toByteArray().size
        return "RML $trid $length"
    }

    fun msgSend(trid: Int, contentLength: Int, isControl: Boolean = false): String {
        val flag = if (isControl) "U" else "A"
        return "MSG $trid $flag $contentLength"
    }

    fun msgBuildBody(message: String, isControl: Boolean = false, typingUser: String? = null): String {
        return if (isControl) {
            "MIME-Version: 1.0\r\nContent-Type: text/x-msmsgscontrol\r\nTypingUser: ${typingUser ?: ""}\r\n\r\n"
        } else {
            "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n$message"
        }
    }

    fun nudgeBuild(): String {
        return "MIME-Version: 1.0\r\nContent-Type: text/x-msnmsgr-datacast\r\n\r\nID: 1"
    }

    fun sbUsrs(trid: Int, email: String, authToken: String): String = "USR $trid $email $authToken"

    fun sbCal(trid: Int, targetEmail: String): String = "CAL $trid $targetEmail"

    fun sbAns(trid: Int, email: String, authToken: String, sessionId: String): String =
        "ANS $trid $email $authToken $sessionId"

    private fun splitEmail(email: String): Pair<String, String> {
        val parts = email.split("@")
        return if (parts.size == 2) {
            parts[0] to parts[1]
        } else {
            email to "msn.com"
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
