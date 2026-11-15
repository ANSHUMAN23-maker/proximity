// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

class VaultTypeConverters {
    @TypeConverter
    fun fromStatus(status: PaymentRecordStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): PaymentRecordStatus =
        runCatching { PaymentRecordStatus.valueOf(value) }.getOrDefault(PaymentRecordStatus.UNVERIFIED)

    @TypeConverter
    fun fromChannel(channel: PaymentChannel): String = channel.name

    @TypeConverter
    fun toChannel(value: String): PaymentChannel =
        runCatching { PaymentChannel.valueOf(value) }.getOrDefault(PaymentChannel.UPI_123PAY)
}

@Database(entities = [PaymentRecord::class], version = 1, exportSchema = true)
@TypeConverters(VaultTypeConverters::class)
abstract class LocalVaultDatabase : RoomDatabase() {
    abstract fun paymentRecordDao(): PaymentRecordDao

    companion object {
        private const val DB_NAME = "vault_encrypted.db"
        private val instanceMutex = Mutex()

        @Volatile
        private var instance: LocalVaultDatabase? = null

        suspend fun getInstance(
            context: Context,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): LocalVaultDatabase {
            instance?.let { return it }
            return withContext(ioDispatcher) {
                instanceMutex.withLock {
                    instance?.let { return@withLock it }
                    System.loadLibrary("sqlcipher")
                    val dbFile = context.getDatabasePath(DB_NAME)
                    val passphrase = VaultKeyManager.getOrCreatePassphrase(context.applicationContext, dbFile)
                    val factory = SupportOpenHelperFactory(passphrase)
                    val built = Room.databaseBuilder(
                        context.applicationContext,
                        LocalVaultDatabase::class.java,
                        DB_NAME
                    )
                        .openHelperFactory(factory)
                        .build()

                    try {
                        built.openHelper.writableDatabase
                    } catch (t: Throwable) {
                        built.close()
                        throw t
                    }

                    instance = built
                    built
                }
            }
        }
    }
}

class RoomPaymentRecordStore(
    private val appContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PaymentRecordStore {

    private suspend fun dao(): PaymentRecordDao =
        LocalVaultDatabase.getInstance(appContext, ioDispatcher).paymentRecordDao()

    override fun observeRecords(): Flow<List<PaymentRecord>> =
        flow<List<PaymentRecord>> {
            emitAll(dao().observeAllRecords())
        }.flowOn(ioDispatcher)

    override suspend fun getById(id: String): PaymentRecord? = withContext(ioDispatcher) {
        dao().getById(id)
    }

    override suspend fun getLatestPending(): PaymentRecord? = withContext(ioDispatcher) {
        dao().getLatestPendingRecord()
    }

    override suspend fun upsert(record: PaymentRecord) = withContext(ioDispatcher) {
        dao().upsert(record)
    }

    override suspend fun expireStalePending(nowMillis: Long) = withContext(ioDispatcher) {
        dao().expireStalePendingRecords(nowMillis)
        Unit
    }

    override suspend fun deleteById(id: String) = withContext(ioDispatcher) {
        dao().deleteById(id)
    }
}
