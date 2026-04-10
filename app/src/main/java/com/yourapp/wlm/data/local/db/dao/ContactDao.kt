package com.yourapp.wlm.data.local.db.dao

import androidx.room.*
import com.yourapp.wlm.data.local.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY status DESC, displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE email = :email")
    suspend fun getContactByEmail(email: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("UPDATE contacts SET status = :status, capabilities = :capabilities WHERE email = :email")
    suspend fun updateStatus(email: String, status: String, capabilities: Long)

    @Query("UPDATE contacts SET personalMessage = :pm WHERE email = :email")
    suspend fun updatePersonalMessage(email: String, pm: String)

    @Query("UPDATE contacts SET isBlocked = :blocked WHERE email = :email")
    suspend fun updateBlockedStatus(email: String, blocked: Boolean)

    @Query("DELETE FROM contacts WHERE email = :email")
    suspend fun deleteContactByEmail(email: String)
}
