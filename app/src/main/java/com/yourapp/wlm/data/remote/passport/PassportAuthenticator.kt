package com.yourapp.wlm.data.remote.passport

import android.util.Log
import com.yourapp.wlm.data.remote.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassportAuthenticator @Inject constructor(
    private val passportAuthService: PassportAuthService
) {
    companion object {
        private const val TAG = "WLM_PassportAuth"
        private const val NEXUS_URL = "https://pp.login.ugnet.gay/rdr/pprdr.asp"
    }

    data class PassportResult(
        val ticket: String,
        val daToken: String,
        val displayName: String,
        val email: String
    )

    suspend fun authenticate(email: String, password: String): Result<PassportResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Passport authentication for $email")

            val daloginUrl = fetchDalLoginUrl()
                .getOrElse { return@withContext Result.failure(it) }

            Log.d(TAG, "DALogin URL: $daloginUrl")

            val loginResult = performLogin(daloginUrl, email, password)
                .getOrElse { return@withContext Result.failure(it) }

            Log.d(TAG, "Passport authentication successful")
            Result.success(loginResult)
        } catch (e: Exception) {
            Log.e(TAG, "Passport authentication failed", e)
            Result.failure(e)
        }
    }

    suspend fun authenticateWithToken(email: String, token: String): Result<PassportResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Authenticating with existing token for $email")
            val ticket = extractTicketFromToken(token)
            if (ticket.isBlank()) {
                return@withContext Result.failure(IllegalStateException("Invalid saved token"))
            }
            Result.success(
                PassportResult(
                    ticket = ticket,
                    daToken = token,
                    displayName = email.substringBefore("@"),
                    email = email
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Token authentication failed", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchDalLoginUrl(): Result<String> {
        return try {
            val response = passportAuthService.getNexusRedirect()
            if (!response.isSuccessful) {
                return Result.failure(IllegalStateException("Nexus redirect failed: ${response.code()}"))
            }

            val rdrHeader = response.headers()["PassportURLs"]
            if (rdrHeader.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("No PassportURLs header found"))
            }

            val daloginUrl = parseDalLoginUrl(rdrHeader)
            if (daloginUrl != null) {
                Result.success(daloginUrl)
            } else {
                Result.failure(IllegalStateException("Could not parse DALogin URL from: $rdrHeader"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseDalLoginUrl(rdrHeader: String): String? {
        val pattern = Pattern.compile("DALogin=([^,]+)")
        val matcher = pattern.matcher(rdrHeader)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    private suspend fun performLogin(daloginUrl: String, email: String, password: String): Result<PassportResult> {
        return try {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val encodedPassword = URLEncoder.encode(password, "UTF-8")

            val authorizationHeader = buildAuthorizationHeader(encodedEmail, encodedPassword)

            val response = passportAuthService.loginWithPassport(daloginUrl, authorizationHeader)

            if (!response.isSuccessful) {
                val errorCode = parseLoginErrorCode(response)
                return Result.failure(IllegalStateException("Login failed with error code: $errorCode"))
            }

            val authInfo = response.headers()["Authentication-Info"]
                ?: response.headers()["Set-Cookie"]
                ?: ""

            val ticket = extractTicket(authInfo)
            val daToken = extractDaToken(authInfo)
            val displayName = extractDisplayName(authInfo)

            if (ticket.isBlank()) {
                return Result.failure(IllegalStateException("No ticket found in authentication response"))
            }

            Result.success(
                PassportResult(
                    ticket = ticket,
                    daToken = daToken.ifBlank { ticket },
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    email = email
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildAuthorizationHeader(encodedEmail: String, encodedPassword: String): String {
        return "Passport1.4 OrgVerb=GET,OrgURL=http%3A%2F%2Fmessenger.msn.com," +
                "sign-in=$encodedEmail,pwd=$encodedPassword,lc=1033,id=507,tw=40,fs=1,ru=-3,lg=1033,kt=0,tpl=103241"
    }

    private fun extractTicket(authInfo: String): String {
        val fromPpPattern = Pattern.compile("from-PP=['\"]?([^'\"\\s,]+)")
        val matcher = fromPpPattern.matcher(authInfo)
        if (matcher.find()) {
            return matcher.group(1)
        }

        val ticketPattern = Pattern.compile("t=['\"]?([^'\"\\s,]+)")
        val ticketMatcher = ticketPattern.matcher(authInfo)
        if (ticketMatcher.find()) {
            return ticketMatcher.group(1)
        }

        return ""
    }

    private fun extractDaToken(authInfo: String): String {
        val daTokenPattern = Pattern.compile("da-token=['\"]?([^'\"\\s,;]+)")
        val matcher = daTokenPattern.matcher(authInfo)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return ""
    }

    private fun extractDisplayName(authInfo: String): String {
        val displayNamePattern = Pattern.compile("MSPProfile=([^,;]+)")
        val matcher = displayNamePattern.matcher(authInfo)
        if (matcher.find()) {
            return matcher.group(1).replace("\"", "")
        }
        return ""
    }

    private fun parseLoginErrorCode(response: retrofit2.Response<okhttp3.ResponseBody>): Int {
        val wwwAuth = response.headers()["WWW-Authenticate"] ?: ""
        val codePattern = Pattern.compile("code=['\"]?(\\d+)")
        val matcher = codePattern.matcher(wwwAuth)
        if (matcher.find()) {
            return matcher.group(1).toInt()
        }
        return response.code()
    }

    private fun extractTicketFromToken(token: String): String {
        if (token.startsWith("t=")) {
            return token
        }
        return "t=$token"
    }
}
