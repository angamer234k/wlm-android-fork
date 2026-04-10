package com.yourapp.wlm.data.remote.soap

import com.yourapp.wlm.data.remote.ServerConfig
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
class StorageService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WLM_Storage"
    }

    suspend fun uploadAvatar(passportTicket: String, avatarData: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val base64Avatar = android.util.Base64.encodeToString(avatarData, android.util.Base64.NO_WRAP)
            val soapRequest = buildUploadAvatarRequest(passportTicket, base64Avatar)
            val request = Request.Builder()
                .url(ServerConfig.STORAGE_SERVICE_URL)
                .header("SOAPAction", "http://schemas.msn.com/storage/2007/SchematizedStore/UploadAvatar")
                .header("Content-Type", "text/xml; charset=utf-8")
                .post(soapRequest.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Upload avatar failed: ${response.code()}"))
            }

            val responseBody = response.body?.string() ?: ""
            val avatarUrl = parseUploadAvatarResponse(responseBody)
            Result.success(avatarUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildUploadAvatarRequest(ticket: String, base64Data: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <TicketToken xmlns="http://schemas.msn.com/storage/2007/">$ticket</TicketToken>
  </soap:Header>
  <soap:Body>
    <UploadAvatar xmlns="http://schemas.msn.com/storage/2007/SchematizedStore">
      <avatarData>$base64Data</avatarData>
    </UploadAvatar>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun parseUploadAvatarResponse(xml: String): String {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "AvatarUrl" || parser.name == "url") {
                            if (parser.nextTag() == XmlPullParser.TEXT) {
                                return parser.text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse upload avatar response", e)
        }
        return ""
    }
}
