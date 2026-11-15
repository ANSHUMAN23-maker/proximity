// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Proximity

package com.proximity.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object VaultKeyManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "vault_db_master_key_v1"
    private const val PREFS_NAME = "vault_key_store"
    private const val PREF_WRAPPED_KEY = "wrapped_passphrase"
    private const val PREF_IV = "wrapped_iv"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    @Synchronized
    fun getOrCreatePassphrase(context: Context, dbFile: File): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrappedB64 = prefs.getString(PREF_WRAPPED_KEY, null)
        val ivB64 = prefs.getString(PREF_IV, null)

        if (wrappedB64 != null && ivB64 != null) {
            val secretKey = getExistingSecretKey()
                ?: throw IllegalStateException("AndroidKeyStore entry missing for existing encrypted vault")
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = Base64.decode(ivB64, Base64.NO_WRAP)
                val wrapped = Base64.decode(wrappedB64, Base64.NO_WRAP)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                val decrypted = cipher.doFinal(wrapped)
                if (decrypted.size != PASSPHRASE_BYTES) {
                    throw IllegalStateException("Decrypted vault passphrase length mismatch")
                }
                decrypted
            } catch (e: AEADBadTagException) {
                throw IllegalStateException("Vault key verification failed; refusing silent wipe", e)
            } catch (e: UnrecoverableKeyException) {
                throw IllegalStateException("Vault Keystore key unrecoverable; refusing silent wipe", e)
            } catch (e: KeyStoreException) {
                throw IllegalStateException("Vault Keystore read failed; refusing silent wipe", e)
            }
        }

        if (dbFile.exists() && dbFile.length() > 0L) {
            throw IllegalStateException("Existing encrypted vault database found without wrapped Keystore key")
        }

        resetKeystoreAndDatabaseFiles(prefs, dbFile)
        val newPassphrase = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val freshKey = generateNewSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, freshKey)
        val iv = cipher.iv
        val wrapped = cipher.doFinal(newPassphrase)
        val committed = prefs.edit()
            .putString(PREF_WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            newPassphrase.fill(0)
            throw IllegalStateException("Failed to persist wrapped vault passphrase")
        }
        return newPassphrase
    }

    private fun getExistingSecretKey(): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generateNewSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun resetKeystoreAndDatabaseFiles(
        prefs: SharedPreferences,
        dbFile: File
    ) {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                ks.deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {
        }
        prefs.edit().clear().commit()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            File(dbFile.absolutePath + suffix).delete()
        }
    }
}
