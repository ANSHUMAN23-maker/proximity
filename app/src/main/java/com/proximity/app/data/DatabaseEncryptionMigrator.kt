package com.proximity.app.data

import android.content.Context
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

internal object DatabaseEncryptionMigrator {

    private const val TAG = "DbEncryptionMigrator"
    private const val PREF_MIGRATED = "db_encrypted"

    fun ensureEncrypted(context: Context, dbName: String, passphrase: String) {
        val prefs = context.getSharedPreferences(DatabaseKeyManager.PREFS_NAME, Context.MODE_PRIVATE)
        val dbFile = context.getDatabasePath(dbName)

        if (!dbFile.exists()) {
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            return
        }

        if (prefs.getBoolean(PREF_MIGRATED, false)) {
            recoverIfUnreadable(dbFile, passphrase)
            return
        }

        try {
            migratePlaintext(dbFile, passphrase)
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            Log.i(TAG, "Transaction database encrypted in place")
        } catch (e: Exception) {
            Log.w(TAG, "Plaintext migration failed - checking whether DB is already encrypted", e)
            prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
            recoverIfUnreadable(dbFile, passphrase)
        }
    }

    private fun migratePlaintext(dbFile: File, passphrase: String) {
        val encryptedFile = File(dbFile.parentFile, "${dbFile.name}.encrypting")
        if (encryptedFile.exists()) encryptedFile.delete()

        val plain = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        val version = plain.version
        try {
            plain.rawExecSQL(
                "ATTACH DATABASE '${encryptedFile.absolutePath}' AS encrypted KEY '$passphrase'"
            )
            plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plain.rawExecSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }

        val encrypted = SQLiteDatabase.openDatabase(
            encryptedFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        try {
            encrypted.version = version
        } finally {
            encrypted.close()
        }
        android.database.sqlite.SQLiteDatabase.deleteDatabase(dbFile)
        if (!encryptedFile.renameTo(dbFile)) {
            throw IllegalStateException("Could not move encrypted database into place")
        }
    }

    private fun recoverIfUnreadable(dbFile: File, passphrase: String) {
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            ).close()
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted DB unreadable with current key - starting fresh (history lost)", e)
            if (isPlaintextSqlite(dbFile)) {
                Log.w(TAG, "Set-aside database is plaintext - deleting rather than retaining")
                dbFile.delete()
            } else {
                val setAside = File(dbFile.parentFile, "${dbFile.name}.unreadable")
                if (setAside.exists()) setAside.delete()
                if (!dbFile.renameTo(setAside)) dbFile.delete()
            }
            File(dbFile.parentFile, "${dbFile.name}-shm").delete()
            File(dbFile.parentFile, "${dbFile.name}-wal").delete()
        }
    }
    private fun isPlaintextSqlite(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(SQLITE_MAGIC_PREFIX.size)
                stream.read(header) == SQLITE_MAGIC_PREFIX.size && header.contentEquals(SQLITE_MAGIC_PREFIX)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read DB header to classify plaintext vs encrypted", e)
            false
        }
    }

    private val SQLITE_MAGIC_PREFIX = "SQLite format 3".toByteArray(Charsets.US_ASCII)
}
