package com.yourapp.wlm.di

import android.content.Context
import com.yourapp.wlm.data.local.datastore.SessionDataStore
import com.yourapp.wlm.data.local.db.WlmDatabase
import com.yourapp.wlm.data.local.db.dao.ContactDao
import com.yourapp.wlm.data.local.db.dao.GroupDao
import com.yourapp.wlm.data.local.db.dao.MessageDao
import com.yourapp.wlm.data.remote.msnp.MsnpHttpGateway
import com.yourapp.wlm.data.remote.passport.PassportAuthService
import com.yourapp.wlm.data.remote.passport.PassportAuthenticator
import com.yourapp.wlm.data.remote.soap.AddressBookService
import com.yourapp.wlm.data.remote.soap.OimService
import com.yourapp.wlm.data.remote.soap.StorageService
import com.yourapp.wlm.data.repository.AuthRepositoryImpl
import com.yourapp.wlm.data.repository.ContactRepositoryImpl
import com.yourapp.wlm.data.repository.MessageRepositoryImpl
import com.yourapp.wlm.data.repository.PresenceRepositoryImpl
import com.yourapp.wlm.domain.repository.AuthRepository
import com.yourapp.wlm.domain.repository.ContactRepository
import com.yourapp.wlm.domain.repository.MessageRepository
import com.yourapp.wlm.domain.repository.PresenceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providePassportAuthService(okHttpClient: OkHttpClient): PassportAuthService {
        return Retrofit.Builder()
            .baseUrl("https://pp.login.ugnet.gay")
            .client(okHttpClient)
            .build()
            .create()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WlmDatabase {
        return WlmDatabase.getInstance(context)
    }

    @Provides
    fun provideContactDao(db: WlmDatabase): ContactDao {
        return db.contactDao()
    }

    @Provides
    fun provideMessageDao(db: WlmDatabase): MessageDao {
        return db.messageDao()
    }

    @Provides
    fun provideGroupDao(db: WlmDatabase): GroupDao {
        return db.groupDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSessionDataStore(@ApplicationContext context: Context): SessionDataStore {
        return SessionDataStore(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(impl: ContactRepositoryImpl): ContactRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindPresenceRepository(impl: PresenceRepositoryImpl): PresenceRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideAddressBookService(okHttpClient: OkHttpClient): AddressBookService {
        return AddressBookService(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideOimService(okHttpClient: OkHttpClient): OimService {
        return OimService(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideStorageService(okHttpClient: OkHttpClient): StorageService {
        return StorageService(okHttpClient)
    }

    @Provides
    @Singleton
    fun providePassportAuthenticator(service: PassportAuthService): PassportAuthenticator {
        return PassportAuthenticator(service)
    }

    @Provides
    @Singleton
    fun provideHttpGateway(okHttpClient: OkHttpClient): MsnpHttpGateway {
        return MsnpHttpGateway(okHttpClient)
    }
}
