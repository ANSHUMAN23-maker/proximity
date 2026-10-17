package com.proximity.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Transaction::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val DB_NAME = "Proximity_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    System.loadLibrary("sqlcipher")
                    val passphrase = DatabaseKeyManager.getOrCreatePassphrase(appContext)
                    DatabaseEncryptionMigrator.ensureEncrypted(appContext, DB_NAME, passphrase)
                    val instance = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DB_NAME
                    )
                        .openHelperFactory(
                            SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8))
                        )
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
        suspend fun getDatabaseSafe(context: Context): AppDatabase =
            withContext(Dispatchers.IO) {
                getDatabase(context)
            }
    }
}
