package com.yourapp.wlm.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yourapp.wlm.data.local.db.dao.ContactDao
import com.yourapp.wlm.data.local.db.dao.GroupDao
import com.yourapp.wlm.data.local.db.dao.MessageDao
import com.yourapp.wlm.data.local.db.entity.ContactEntity
import com.yourapp.wlm.data.local.db.entity.ConversationEntity
import com.yourapp.wlm.data.local.db.entity.GroupEntity
import com.yourapp.wlm.data.local.db.entity.MessageEntity

@Database(
    entities = [
        ContactEntity::class,
        MessageEntity::class,
        GroupEntity::class,
        ConversationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WlmDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao

    companion object {
        @Volatile
        private var INSTANCE: WlmDatabase? = null

        fun getInstance(context: Context): WlmDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WlmDatabase::class.java,
                    "wlm_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
