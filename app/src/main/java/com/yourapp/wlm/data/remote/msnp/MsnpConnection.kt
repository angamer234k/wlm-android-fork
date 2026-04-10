package com.yourapp.wlm.data.remote.msnp

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLSocketFactory

class MsnpConnection(
    private val host: String,
    private val port: Int,
    private val useSsl: Boolean = false
) {
    companion object {
        private const val TAG = "WLM_MsnpConnection"
        private const val CONNECTION_TIMEOUT = 10000L
        private const val READ_TIMEOUT = 60000L
    }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val transactionId = AtomicInteger(1)
    private val _commandChannel = Channel<String>(Channel.UNLIMITED, BufferOverflow.DROP_OLDEST)
    private val _connectionState = Channel<ConnectionState>(Channel.CONFLATED)
    var isConnected = false
        private set

    val commandChannel = _commandChannel
    val connectionState = _connectionState

    enum class ConnectionState {
        CONNECTED, DISCONNECTED, ERROR
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to $host:$port (SSL=$useSsl)")
            socket = if (useSsl) {
                SSLSocketFactory.getDefault().createSocket(host, port).apply {
                    soTimeout = READ_TIMEOUT.toInt()
                }
            } else {
                Socket(host, port).apply {
                    soTimeout = READ_TIMEOUT.toInt()
                }
            }

            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
            isConnected = true
            _connectionState.trySend(ConnectionState.CONNECTED)
            Log.d(TAG, "Connected to $host:$port")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $host:$port", e)
            isConnected = false
            _connectionState.trySend(ConnectionState.ERROR)
            Result.failure(e)
        }
    }

    fun startReading() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                while (isConnected) {
                    val line = reader?.readLine()
                    if (line == null) {
                        Log.w(TAG, "Connection closed by server")
                        isConnected = false
                        _connectionState.trySend(ConnectionState.DISCONNECTED)
                        break
                    }
                    Log.d(TAG, "S→C: $line")
                    _commandChannel.trySend(line)
                }
            } catch (e: Exception) {
                if (isConnected) {
                    Log.e(TAG, "Error reading from server", e)
                    isConnected = false
                    _connectionState.trySend(ConnectionState.ERROR)
                }
            }
        }
    }

    suspend fun sendCommand(command: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                return@withContext Result.failure(IllegalStateException("Not connected"))
            }
            Log.d(TAG, "C→S: $command")
            writer?.write("$command\r\n")
            writer?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command: $command", e)
            Result.failure(e)
        }
    }

    suspend fun sendCommandWithBody(command: String, body: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isConnected) {
                return@withContext Result.failure(IllegalStateException("Not connected"))
            }
            Log.d(TAG, "C→S: $command")
            Log.d(TAG, "C→S Body: $body")
            writer?.write("$command\r\n")
            writer?.write(body)
            writer?.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send command with body: $command", e)
            Result.failure(e)
        }
    }

    fun nextTransactionId(): Int = transactionId.getAndIncrement()

    suspend fun disconnect() {
        try {
            isConnected = false
            socket?.close()
            socket = null
            reader = null
            writer = null
            _connectionState.trySend(ConnectionState.DISCONNECTED)
            Log.d(TAG, "Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
    }
}
