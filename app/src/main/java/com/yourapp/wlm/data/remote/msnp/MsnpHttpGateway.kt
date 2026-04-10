package com.yourapp.wlm.data.remote.msnp

import android.util.Log
import com.yourapp.wlm.data.remote.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MsnpHttpGateway @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "WLM_HttpGateway"
    }

    private var sessionId: String? = null
    private var isConnected = false

    suspend fun connect(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "HTTP Gateway connect for $email")
            val url = "${ServerConfig.HTTP_GATEWAY_URL}?Action=open&Server=NS&IP=${ServerConfig.NS_HOST}"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Gateway connect failed: ${response.code()}"))
            }

            val body = response.body?.string() ?: ""
            sessionId = parseSessionId(body)
            if (sessionId.isNullOrEmpty()) {
                return@withContext Result.failure(IllegalStateException("No session ID returned"))
            }

            isConnected = true
            Log.d(TAG, "HTTP Gateway connected, session: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Gateway connect failed", e)
            Result.failure(e)
        }
    }

    suspend fun sendCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sessionId == null) {
                return@withContext Result.failure(IllegalStateException("Not connected"))
            }

            Log.d(TAG, "HTTP Gateway send: $command")
            val url = "${ServerConfig.HTTP_GATEWAY_URL}?SessionID=$sessionId"
            val request = Request.Builder()
                .url(url)
                .post(command.toRequestBody(null))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Gateway send failed: ${response.code()}"))
            }

            val body = response.body?.string() ?: ""
            Result.success(body)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Gateway send failed", e)
            Result.failure(e)
        }
    }

    suspend fun poll(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sessionId == null) {
                return@withContext Result.failure(IllegalStateException("Not connected"))
            }

            val url = "${ServerConfig.HTTP_GATEWAY_URL}?SessionID=$sessionId"
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody(null))
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Gateway poll failed: ${response.code()}"))
            }

            val body = response.body?.string() ?: ""
            Result.success(body)
        } catch (e: Exception) {
            Log.e(TAG, "HTTP Gateway poll failed", e)
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sessionId = null
            isConnected = false
            Log.d(TAG, "HTTP Gateway disconnected")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isActive() = isConnected && sessionId != null

    private fun parseSessionId(body: String): String? {
        val pattern = """SessionID=(\w+)""".toRegex()
        return pattern.find(body)?.groupValues?.getOrNull(1)
            ?: body.takeIf { it.isNotBlank() }
    }
}
