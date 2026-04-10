package com.yourapp.wlm.data.remote.soap

import com.yourapp.wlm.data.remote.ServerConfig
import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OimService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WLM_OIM"
        private const val SOAP_ACTION_GET_METADATA = "http://messenger.msn.com/ws/2004/09/oim/retrieve/GetMetadata"
        private const val SOAP_ACTION_GET_MESSAGE = "http://messenger.msn.com/ws/2004/09/oim/retrieve/GetMessage"
    }

    data class OimMetadata(
        val messageId: String,
        val fromEmail: String,
        val timestamp: Long,
        val preview: String
    )

    suspend fun getMetadata(passportTicket: String): Result<List<OimMetadata>> = withContext(Dispatchers.IO) {
        try {
            val soapRequest = buildGetMetadataRequest(passportTicket)
            val request = Request.Builder()
                .url(ServerConfig.OIM_SERVICE_URL)
                .header("SOAPAction", SOAP_ACTION_GET_METADATA)
                .header("Content-Type", "text/xml; charset=utf-8")
                .post(soapRequest.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("OIM metadata failed: ${response.code()}"))
            }

            val responseBody = response.body?.string() ?: ""
            val metadata = parseMetadataResponse(responseBody)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessage(passportTicket: String, messageId: String): Result<Message?> = withContext(Dispatchers.IO) {
        try {
            val soapRequest = buildGetMessageRequest(passportTicket, messageId)
            val request = Request.Builder()
                .url(ServerConfig.OIM_SERVICE_URL)
                .header("SOAPAction", SOAP_ACTION_GET_MESSAGE)
                .header("Content-Type", "text/xml; charset=utf-8")
                .post(soapRequest.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("OIM get message failed: ${response.code()}"))
            }

            val responseBody = response.body?.string() ?: ""
            val message = parseMessageResponse(responseBody)
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun retrieveAllOfflineMessages(passportTicket: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        try {
            val metadataResult = getMetadata(passportTicket)
            if (metadataResult.isFailure) return@withContext metadataResult

            val messages = mutableListOf<Message>()
            metadataResult.getOrNull()?.forEach { meta ->
                val msgResult = getMessage(passportTicket, meta.messageId)
                msgResult.getOrNull()?.let { messages.add(it) }
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildGetMetadataRequest(ticket: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <TicketToken xmlns="http://messenger.msn.com/ws/2004/09/oim/">$ticket</TicketToken>
  </soap:Header>
  <soap:Body>
    <GetMetadata xmlns="http://messenger.msn.com/ws/2004/09/oim/retrieve/"/>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun buildGetMessageRequest(ticket: String, messageId: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <TicketToken xmlns="http://messenger.msn.com/ws/2004/09/oim/">$ticket</TicketToken>
  </soap:Header>
  <soap:Body>
    <GetMessage xmlns="http://messenger.msn.com/ws/2004/09/oim/retrieve/">
      <messageId>$messageId</messageId>
    </GetMessage>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun parseMetadataResponse(xml: String): List<OimMetadata> {
        val metadataList = mutableListOf<OimMetadata>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentId: String? = null
            var currentFrom: String? = null
            var currentTimestamp: Long = 0L
            var currentPreview: String = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "MessageMetadata", "OimMessage" -> {
                                currentId = null
                                currentFrom = null
                                currentTimestamp = 0L
                                currentPreview = ""
                            }
                            "MessageId", "id" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentId = parser.text
                                }
                            }
                            "From", "sender" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentFrom = parser.text
                                }
                            }
                            "Timestamp", "time" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    try {
                                        currentTimestamp = parser.text.toLong()
                                    } catch (_: NumberFormatException) {
                                        currentTimestamp = System.currentTimeMillis()
                                    }
                                }
                            }
                            "Preview", "subject" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentPreview = parser.text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "MessageMetadata" || parser.name == "OimMessage") {
                            if (currentId != null) {
                                metadataList.add(
                                    OimMetadata(
                                        messageId = currentId!!,
                                        fromEmail = currentFrom ?: "",
                                        timestamp = currentTimestamp,
                                        preview = currentPreview
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse OIM metadata", e)
        }
        return metadataList
    }

    private fun parseMessageResponse(xml: String): Message? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var body = ""
            var from = ""
            var timestamp = System.currentTimeMillis()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Body", "messageBody" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    body = parser.text
                                }
                            }
                            "From", "sender" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    from = parser.text
                                }
                            }
                            "Timestamp", "time" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    try {
                                        timestamp = parser.text.toLong()
                                    } catch (_: NumberFormatException) {
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (body.isNotBlank()) {
                return Message(
                    conversationId = from,
                    senderEmail = from,
                    body = body,
                    timestamp = timestamp,
                    isOutgoing = false,
                    isOffline = true,
                    messageType = MessageType.TEXT
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse OIM message", e)
        }
        return null
    }
}
