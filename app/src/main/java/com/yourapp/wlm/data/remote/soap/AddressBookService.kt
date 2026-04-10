package com.yourapp.wlm.data.remote.soap

import com.yourapp.wlm.data.remote.ServerConfig
import com.yourapp.wlm.data.local.db.entity.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddressBookService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WLM_AddressBook"
        private const val SOAP_ACTION_FIND_CONTACTS = "http://www.msn.com/webservices/AddressBook/ABFindContactsPaged"
    }

    suspend fun findContacts(passportTicket: String): Result<List<ContactEntity>> = withContext(Dispatchers.IO) {
        try {
            val soapRequest = buildFindContactsRequest(passportTicket)
            val request = Request.Builder()
                .url(ServerConfig.ABSERVICE_URL)
                .header("SOAPAction", SOAP_ACTION_FIND_CONTACTS)
                .header("Content-Type", "text/xml; charset=utf-8")
                .post(soapRequest.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("SOAP request failed: ${response.code()}"))
            }

            val responseBody = response.body?.string() ?: ""
            val contacts = parseContactsResponse(responseBody)
            Result.success(contacts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(passportTicket: String, displayName: String, personalMessage: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val soapRequest = buildUpdateProfileRequest(passportTicket, displayName, personalMessage)
            val request = Request.Builder()
                .url(ServerConfig.ABSERVICE_URL)
                .header("SOAPAction", "http://www.msn.com/webservices/AddressBook/ABContactUpdate")
                .header("Content-Type", "text/xml; charset=utf-8")
                .post(soapRequest.toRequestBody("text/xml; charset=utf-8".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Update profile failed: ${response.code()}"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFindContactsRequest(ticket: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <ABApplicationHeader xmlns="http://www.msn.com/webservices/AddressBook">
      <ApplicationId>CFE80F9D-180F-4399-82AB-413F33A1FA3C</ApplicationId>
      <IsMigration>false</IsMigration>
      <PartnerScenario>Initial</PartnerScenario>
    </ABApplicationHeader>
    <ABAuthHeader xmlns="http://www.msn.com/webservices/AddressBook">
      <ManagedGroupRequest>false</ManagedGroupRequest>
      <TicketToken>$ticket</TicketToken>
    </ABAuthHeader>
  </soap:Header>
  <soap:Body>
    <ABFindContactsPaged xmlns="http://www.msn.com/webservices/AddressBook">
      <filterOptions>
        <LastChanged>0001-01-01T00:00:00.0000000-07:00</LastChanged>
      </filterOptions>
      <abView>MessengerClient8</abView>
      <deltas_only>false</deltas_only>
    </ABFindContactsPaged>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun buildUpdateProfileRequest(ticket: String, displayName: String, personalMessage: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
  <soap:Header>
    <ABApplicationHeader xmlns="http://www.msn.com/webservices/AddressBook">
      <ApplicationId>CFE80F9D-180F-4399-82AB-413F33A1FA3C</ApplicationId>
    </ABApplicationHeader>
    <ABAuthHeader xmlns="http://www.msn.com/webservices/AddressBook">
      <TicketToken>$ticket</TicketToken>
    </ABAuthHeader>
  </soap:Header>
  <soap:Body>
    <ABContactUpdate xmlns="http://www.msn.com/webservices/AddressBook">
      <contact>
        <DisplayName>${escapeXml(displayName)}</DisplayName>
        <PersonalMessage>${escapeXml(personalMessage)}</PersonalMessage>
      </contact>
    </ABContactUpdate>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun parseContactsResponse(xml: String): List<ContactEntity> {
        val contacts = mutableListOf<ContactEntity>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentEmail: String? = null
            var currentDisplayName: String? = null
            var currentPm: String? = null
            var currentStatus = "FLN"
            var currentGroupId: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Contact", "ContactEntry" -> {
                                currentEmail = null
                                currentDisplayName = null
                                currentPm = ""
                                currentStatus = "FLN"
                            }
                            "ContactId", "Email", "contactId" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentEmail = parser.text
                                }
                            }
                            "DisplayName", "displayName", "FriendlyName" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentDisplayName = parser.text
                                }
                            }
                            "PersonalMessage", "PSM" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentPm = parser.text
                                }
                            }
                            "Status", "Presence" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentStatus = parser.text
                                }
                            }
                            "GroupId" -> {
                                if (parser.nextTag() == XmlPullParser.TEXT) {
                                    currentGroupId = parser.text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Contact" || parser.name == "ContactEntry") {
                            if (currentEmail != null) {
                                contacts.add(
                                    ContactEntity(
                                        email = currentEmail!!,
                                        displayName = currentDisplayName ?: currentEmail!!,
                                        personalMessage = currentPm ?: "",
                                        status = currentStatus,
                                        groupIds = currentGroupId?.let { "[$it]" } ?: "[]"
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse contacts XML", e)
        }
        return contacts
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
