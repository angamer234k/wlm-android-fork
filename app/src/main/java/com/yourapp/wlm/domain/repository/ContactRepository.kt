package com.yourapp.wlm.domain.repository

import com.yourapp.wlm.domain.model.Contact
import com.yourapp.wlm.domain.model.ContactGroup
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getContacts(): Flow<List<Contact>>
    fun getGroupedContacts(): Flow<List<ContactGroup>>
    suspend fun addContact(email: String, groupId: String? = null): Result<Unit>
    suspend fun removeContact(email: String): Result<Unit>
    suspend fun blockContact(email: String): Result<Unit>
    suspend fun unblockContact(email: String): Result<Unit>
    suspend fun syncContactsFromServer(): Result<Unit>
    suspend fun updateContactStatus(email: String, status: String, capabilities: Long)
    suspend fun updateContactPersonalMessage(email: String, pm: String)
    suspend fun getContactByEmail(email: String): Contact?
}
