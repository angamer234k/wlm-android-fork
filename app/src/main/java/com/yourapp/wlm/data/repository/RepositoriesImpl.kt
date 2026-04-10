package com.yourapp.wlm.data.repository

import android.util.Log
import com.yourapp.wlm.data.local.datastore.SessionDataStore
import com.yourapp.wlm.data.local.db.WlmDatabase
import com.yourapp.wlm.data.local.db.entity.ContactEntity
import com.yourapp.wlm.data.remote.msnp.MsnpCommandBuilder
import com.yourapp.wlm.data.remote.msnp.MsnpConnection
import com.yourapp.wlm.data.remote.msnp.MsnpNotificationHandler
import com.yourapp.wlm.data.remote.msnp.MsnpNotificationHandler.NsEvent
import com.yourapp.wlm.data.remote.msnp.MsnpSwitchboardSession
import com.yourapp.wlm.data.remote.msnp.ServerConfig
import com.yourapp.wlm.data.remote.passport.PassportAuthenticator
import com.yourapp.wlm.data.remote.soap.AddressBookService
import com.yourapp.wlm.data.remote.soap.OimService
import com.yourapp.wlm.domain.model.Contact
import com.yourapp.wlm.domain.model.ContactGroup
import com.yourapp.wlm.domain.model.Message
import com.yourapp.wlm.domain.model.MessageType
import com.yourapp.wlm.domain.model.PresenceStatus
import com.yourapp.wlm.domain.model.UserProfile
import com.yourapp.wlm.domain.repository.AuthRepository
import com.yourapp.wlm.domain.repository.ContactRepository
import com.yourapp.wlm.domain.repository.MessageRepository
import com.yourapp.wlm.domain.repository.PresenceRepository
import dagger.Lazy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceRepositoryImpl @Inject constructor(
    private val database: Lazy<WlmDatabase>,
    private val sessionDataStore: SessionDataStore,
    private val passportAuthenticator: PassportAuthenticator
) : PresenceRepository {

    companion object {
        private const val TAG = "WLM_PresenceRepo"
    }

    private val _currentStatus = MutableStateFlow(PresenceStatus.ONLINE)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _typingContacts = MutableStateFlow<Set<String>>(emptySet())

    private var nsHandler: MsnpNotificationHandler? = null
    private var nsConnection: MsnpConnection? = null
    private var connectionScope: CoroutineScope? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10

    override suspend fun changeStatus(status: PresenceStatus, capabilities: Long): Result<Unit> {
        return try {
            val result = nsHandler?.changePresence(status, capabilities)
            if (result?.isSuccess == true) {
                _currentStatus.value = status
                sessionDataStore.saveUserProfile(
                    sessionDataStore.userEmail.first(),
                    sessionDataStore.savedDisplayName.first(),
                    sessionDataStore.personalMessage.first(),
                    status.msnpCode
                )
            }
            result ?: Result.failure(IllegalStateException("Not connected"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change status", e)
            Result.failure(e)
        }
    }

    override suspend fun setPersonalMessage(message: String): Result<Unit> {
        return try {
            val result = nsHandler?.setPersonalMessage(message)
            if (result?.isSuccess == true) {
                sessionDataStore.saveUserProfile(
                    sessionDataStore.userEmail.first(),
                    sessionDataStore.savedDisplayName.first(),
                    message,
                    _currentStatus.value.msnpCode
                )
            }
            result ?: Result.failure(IllegalStateException("Not connected"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set PM", e)
            Result.failure(e)
        }
    }

    override fun getCurrentStatus(): Flow<PresenceStatus> = _currentStatus.asStateFlow()

    override fun getContactPresence(email: String): Flow<PresenceStatus> = flow {
        val contact = database.get().contactDao().getContactByEmail(email)
        emit(contact?.let { PresenceStatus.fromMsnpCode(it.status) } ?: PresenceStatus.OFFLINE)
    }

    override fun isTyping(contactEmail: String): Flow<Boolean> = _typingContacts.asStateFlow().map { contacts ->
        contactEmail in contacts
    }

    override suspend fun startConnection(): Result<Unit> {
        return try {
            _connectionState.value = ConnectionState.CONNECTING
            reconnectAttempts = 0

            val email = sessionDataStore.userEmail.first()
            val ticket = sessionDataStore.savedToken.first()

            if (email.isBlank() || ticket.isBlank()) {
                return Result.failure(IllegalStateException("No credentials available"))
            }

            nsConnection = MsnpConnection(ServerConfig.NS_HOST, ServerConfig.NS_PORT)
            val connectResult = nsConnection!!.connect()
            if (connectResult.isFailure) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return connectResult
            }

            nsHandler = MsnpNotificationHandler(nsConnection!!, email, ticket)
            nsHandler!!.start()

            collectNsEvents()

            val handshakeResult = nsHandler!!.performHandshake()
            if (handshakeResult.isFailure) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return handshakeResult
            }

            val ticketResult = nsHandler!!.sendTicketToServer(ticket)
            if (ticketResult.isFailure) {
                _connectionState.value = ConnectionState.DISCONNECTED
                return ticketResult
            }

            _connectionState.value = ConnectionState.CONNECTED
            reconnectAttempts = 0
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start connection", e)
            _connectionState.value = ConnectionState.DISCONNECTED
            Result.failure(e)
        }
    }

    override suspend fun stopConnection() {
        try {
            nsHandler?.disconnect()
            nsConnection?.disconnect()
            nsHandler = null
            nsConnection = null
            connectionScope?.cancel()
            connectionScope = null
            _connectionState.value = ConnectionState.DISCONNECTED
            reconnectAttempts = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping connection", e)
        }
    }

    override fun getConnectionState(): Flow<ConnectionState> = _connectionState.asStateFlow()

    private fun collectNsEvents() {
        val handler = nsHandler ?: return
        connectionScope = CoroutineScope(SupervisorJob())
        connectionScope!!.launch {
            for (event in handler.eventChannel) {
                when (event) {
                    is NsEvent.ContactPresence -> {
                        database.get().contactDao().updateStatus(event.email, event.status.msnpCode, event.capabilities)
                    }
                    is NsEvent.PersonalMessageChanged -> {
                        database.get().contactDao().updatePersonalMessage(event.email, event.pm)
                    }
                    is NsEvent.TypingNotification -> {
                        val current = _typingContacts.value.toMutableSet()
                        current.add(event.email)
                        _typingContacts.value = current
                        delay(3000)
                        val updated = _typingContacts.value.toMutableSet()
                        updated.remove(event.email)
                        _typingContacts.value = updated
                    }
                    is NsEvent.ReconnectRequired -> {
                        handleReconnect(event.reason)
                    }
                    is NsEvent.Disconnected -> {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        handleReconnect("Server disconnected")
                    }
                    is NsEvent.ServerError -> {
                        if (event.code == 911) {
                            sessionDataStore.clearSession()
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun handleReconnect(reason: String) {
        Log.w(TAG, "Reconnect required: $reason")
        connectionScope?.launch {
            if (reconnectAttempts < maxReconnectAttempts) {
                val delayMs = minOf(5000L * (1L shl reconnectAttempts), 120000L)
                Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt ${reconnectAttempts + 1})")
                _connectionState.value = ConnectionState.RECONNECTING
                delay(delayMs)
                reconnectAttempts++
                startConnection()
            } else {
                Log.e(TAG, "Max reconnect attempts reached")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }
}

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val database: Lazy<WlmDatabase>,
    private val sessionDataStore: SessionDataStore,
    private val addressBookService: AddressBookService,
    private val presenceRepository: PresenceRepositoryImpl
) : ContactRepository {

    companion object {
        private const val TAG = "WLM_ContactRepo"
    }

    override fun getContacts(): Flow<List<Contact>> = flow {
        database.get().contactDao().getAllContacts().collect { entities ->
            emit(entities.map { toContact(it) })
        }
    }

    override fun getGroupedContacts(): Flow<List<ContactGroup>> = flow {
        database.get().contactDao().getAllContacts().collect { entities ->
            val contacts = entities.map { toContact(it) }

            val onlineContacts = contacts.filter { it.status != PresenceStatus.OFFLINE }
                .sortedBy { it.displayName.lowercase() }
            val offlineContacts = contacts.filter { it.status == PresenceStatus.OFFLINE }
                .sortedBy { it.displayName.lowercase() }

            val groups = mutableListOf<ContactGroup>()
            groups.add(
                ContactGroup(
                    groupId = "online",
                    name = "Online",
                    sortOrder = 0,
                    contacts = onlineContacts
                )
            )
            groups.add(
                ContactGroup(
                    groupId = "offline",
                    name = "Offline",
                    sortOrder = 1,
                    contacts = offlineContacts
                )
            )

            emit(groups)
        }
    }

    override suspend fun addContact(email: String, groupId: String?): Result<Unit> {
        return try {
            val result = presenceRepository.nsHandler?.addContact(email)
            if (result?.isSuccess == true) {
                database.get().contactDao().insertContact(
                    ContactEntity(
                        email = email,
                        displayName = email.substringBefore("@"),
                        status = "FLN",
                        groupIds = groupId?.let { "[\"$it\"]" } ?: "[]"
                    )
                )
            }
            result ?: Result.failure(IllegalStateException("Not connected"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add contact", e)
            Result.failure(e)
        }
    }

    override suspend fun removeContact(email: String): Result<Unit> {
        return try {
            val result = presenceRepository.nsHandler?.removeContact(email)
            if (result?.isSuccess == true) {
                database.get().contactDao().deleteContactByEmail(email)
            }
            result ?: Result.failure(IllegalStateException("Not connected"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove contact", e)
            Result.failure(e)
        }
    }

    override suspend fun blockContact(email: String): Result<Unit> {
        return try {
            database.get().contactDao().updateBlockedStatus(email, true)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to block contact", e)
            Result.failure(e)
        }
    }

    override suspend fun unblockContact(email: String): Result<Unit> {
        return try {
            database.get().contactDao().updateBlockedStatus(email, false)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unblock contact", e)
            Result.failure(e)
        }
    }

    override suspend fun syncContactsFromServer(): Result<Unit> {
        return try {
            val ticket = sessionDataStore.savedToken.first()
            if (ticket.isBlank()) {
                return Result.failure(IllegalStateException("No passport token"))
            }

            val result = addressBookService.findContacts(ticket)
            if (result.isSuccess) {
                val contacts = result.getOrNull() ?: emptyList()
                database.get().contactDao().insertContacts(contacts)
                Log.d(TAG, "Synced ${contacts.size} contacts from server")
            }
            result.map { }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync contacts", e)
            Result.failure(e)
        }
    }

    override suspend fun updateContactStatus(email: String, status: String, capabilities: Long) {
        try {
            database.get().contactDao().updateStatus(email, status, capabilities)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update status for $email", e)
        }
    }

    override suspend fun updateContactPersonalMessage(email: String, pm: String) {
        try {
            database.get().contactDao().updatePersonalMessage(email, pm)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update PM for $email", e)
        }
    }

    override suspend fun getContactByEmail(email: String): Contact? {
        return try {
            database.get().contactDao().getContactByEmail(email)?.let { toContact(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contact", e)
            null
        }
    }

    private fun toContact(entity: ContactEntity): Contact {
        return Contact(
            email = entity.email,
            displayName = entity.displayName,
            personalMessage = entity.personalMessage,
            status = PresenceStatus.fromMsnpCode(entity.status),
            groupIds = try {
                Json { ignoreUnknownKeys = true }.decodeFromString<List<String>>(entity.groupIds)
            } catch (e: Exception) {
                emptyList()
            },
            avatarUrl = entity.avatarUrl,
            isBlocked = entity.isBlocked,
            listFlags = entity.listFlags,
            lastSeen = entity.lastSeen,
            capabilities = entity.capabilities
        )
    }
}

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val database: Lazy<WlmDatabase>,
    private val sessionDataStore: SessionDataStore,
    private val presenceRepository: PresenceRepositoryImpl
) : MessageRepository {

    companion object {
        private const val TAG = "WLM_MessageRepo"
    }

    private val _incomingMessageFlow = MutableSharedFlow<Message>(extraBufferCapacity = 64)

    override fun getMessagesForConversation(contactEmail: String): Flow<List<Message>> = flow {
        database.get().messageDao().getMessagesForConversation(contactEmail).collect { entities ->
            emit(entities.map { toMessage(it) })
        }
    }

    override suspend fun sendMessage(contactEmail: String, body: String): Result<Unit> {
        return try {
            val sbSession = getOrCreateSbSession(contactEmail)
            if (sbSession == null) {
                return Result.failure(IllegalStateException("Could not establish SB session"))
            }

            val message = Message(
                conversationId = contactEmail,
                senderEmail = sessionDataStore.userEmail.first(),
                body = body,
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                messageType = MessageType.TEXT,
                isDelivered = false
            )

            saveOutgoingMessage(message)
            _incomingMessageFlow.tryEmit(message)

            val sendResult = sbSession.sendMessage(body)
            if (sendResult.isSuccess) {
                database.get().messageDao().markMessagesAsRead(contactEmail)
            }
            sendResult
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            Result.failure(e)
        }
    }

    override suspend fun sendNudge(contactEmail: String): Result<Unit> {
        return try {
            val sbSession = getOrCreateSbSession(contactEmail)
            if (sbSession == null) {
                return Result.failure(IllegalStateException("Could not establish SB session"))
            }

            val message = Message(
                conversationId = contactEmail,
                senderEmail = sessionDataStore.userEmail.first(),
                body = "",
                timestamp = System.currentTimeMillis(),
                isOutgoing = true,
                messageType = MessageType.NUDGE
            )

            saveOutgoingMessage(message)
            _incomingMessageFlow.tryEmit(message)

            sbSession.sendNudge()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send nudge", e)
            Result.failure(e)
        }
    }

    override suspend fun sendTyping(contactEmail: String): Result<Unit> {
        return try {
            val sbSession = getOrCreateSbSession(contactEmail)
            if (sbSession == null) {
                return Result.failure(IllegalStateException("Could not establish SB session"))
            }
            sbSession.sendTyping()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send typing", e)
            Result.failure(e)
        }
    }

    override suspend fun saveIncomingMessage(message: Message) {
        try {
            val entity = MessageEntity(
                conversationId = message.conversationId,
                senderEmail = message.senderEmail,
                body = message.body,
                timestamp = message.timestamp,
                isOutgoing = message.isOutgoing,
                isOffline = message.isOffline,
                messageType = message.messageType.name,
                isDelivered = message.isDelivered
            )
            database.get().messageDao().insertMessage(entity)
            _incomingMessageFlow.tryEmit(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save incoming message", e)
        }
    }

    override suspend fun saveOutgoingMessage(message: Message) {
        try {
            val entity = MessageEntity(
                conversationId = message.conversationId,
                senderEmail = message.senderEmail,
                body = message.body,
                timestamp = message.timestamp,
                isOutgoing = message.isOutgoing,
                isOffline = message.isOffline,
                messageType = message.messageType.name,
                isDelivered = message.isDelivered
            )
            database.get().messageDao().insertMessage(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save outgoing message", e)
        }
    }

    override suspend fun markMessagesAsRead(contactEmail: String) {
        try {
            database.get().messageDao().markMessagesAsRead(contactEmail)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark as read", e)
        }
    }

    override suspend fun loadOfflineMessages(): Result<Unit> {
        return try {
            val ticket = sessionDataStore.savedToken.first()
            if (ticket.isBlank()) {
                return Result.failure(IllegalStateException("No passport token"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load offline messages", e)
            Result.failure(e)
        }
    }

    override suspend fun getUnreadCount(contactEmail: String): Int {
        return try {
            database.get().messageDao().getUnreadCount(contactEmail)
        } catch (e: Exception) {
            0
        }
    }

    override fun observeIncomingMessages(): Flow<Message> = _incomingMessageFlow

    private val sbSessions = mutableMapOf<String, MsnpSwitchboardSession>()

    private suspend fun getOrCreateSbSession(contactEmail: String): MsnpSwitchboardSession? {
        return try {
            val existingSession = sbSessions[contactEmail]
            if (existingSession?.isConnected == true) {
                return existingSession
            }

            val email = sessionDataStore.userEmail.first()
            val ticket = sessionDataStore.savedToken.first()

            presenceRepository.nsHandler?.requestSwitchboard()

            delay(2000)

            val session = MsnpSwitchboardSession(
                host = ServerConfig.NS_HOST,
                port = ServerConfig.NS_PORT,
                authToken = ticket,
                userEmail = email,
                contactEmail = contactEmail
            )

            val connectResult = session.connect()
            if (connectResult.isSuccess) {
                sbSessions[contactEmail] = session
                session
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SB session", e)
            null
        }
    }

    private fun toMessage(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            conversationId = entity.conversationId,
            senderEmail = entity.senderEmail,
            body = entity.body,
            timestamp = entity.timestamp,
            isOutgoing = entity.isOutgoing,
            isOffline = entity.isOffline,
            messageType = MessageType.valueOf(entity.messageType),
            isDelivered = entity.isDelivered
        )
    }
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val sessionDataStore: SessionDataStore,
    private val passportAuthenticator: PassportAuthenticator,
    private val presenceRepository: PresenceRepositoryImpl,
    private val contactRepository: ContactRepositoryImpl
) : AuthRepository {

    companion object {
        private const val TAG = "WLM_AuthRepo"
    }

    override suspend fun login(email: String, password: String, rememberMe: Boolean): Result<Unit> {
        return try {
            Log.d(TAG, "Logging in user: $email")

            val authResult = passportAuthenticator.authenticate(email, password)
            if (authResult.isFailure) {
                return authResult.map { }
            }

            val result = authResult.getOrNull()!!
            sessionDataStore.saveLoginSession(
                email = result.email,
                displayName = result.displayName,
                passportToken = result.ticket,
                daToken = result.daToken,
                rememberMe = rememberMe
            )

            val connectionResult = presenceRepository.startConnection()
            if (connectionResult.isFailure) {
                return connectionResult
            }

            contactRepository.syncContactsFromServer()
            contactRepository.loadOfflineMessages()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        try {
            Log.d(TAG, "Logging out user")
            presenceRepository.stopConnection()
            sessionDataStore.clearSession()
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout", e)
        }
    }

    override suspend fun autoLogin(): Result<Unit> {
        return try {
            val loggedIn = sessionDataStore.isLoggedIn.first()
            if (!loggedIn) {
                return Result.failure(IllegalStateException("No active session"))
            }

            val email = sessionDataStore.userEmail.first()
            val token = sessionDataStore.savedToken.first()

            if (email.isBlank() || token.isBlank()) {
                return Result.failure(IllegalStateException("No saved credentials"))
            }

            val authResult = passportAuthenticator.authenticateWithToken(email, token)
            if (authResult.isFailure) {
                sessionDataStore.clearSession()
                return authResult.map { }
            }

            val connectionResult = presenceRepository.startConnection()
            if (connectionResult.isFailure) {
                return connectionResult
            }

            contactRepository.syncContactsFromServer()
            contactRepository.loadOfflineMessages()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-login failed", e)
            Result.failure(e)
        }
    }

    override fun isLoggedIn(): Flow<Boolean> = sessionDataStore.isLoggedIn

    override fun getUserProfile(): Flow<com.yourapp.wlm.domain.model.UserProfile?> = flow {
        val email = sessionDataStore.userEmail.first()
        val displayName = sessionDataStore.savedDisplayName.first()
        val pm = sessionDataStore.personalMessage.first()
        val statusStr = sessionDataStore.userStatus.first()

        if (email.isBlank()) {
            emit(null)
        } else {
            emit(
                com.yourapp.wlm.domain.model.UserProfile(
                    email = email,
                    displayName = displayName,
                    personalMessage = pm,
                    status = PresenceStatus.fromMsnpCode(statusStr)
                )
            )
        }
    }

    override suspend fun clearSession() {
        sessionDataStore.clearSession()
    }
}
