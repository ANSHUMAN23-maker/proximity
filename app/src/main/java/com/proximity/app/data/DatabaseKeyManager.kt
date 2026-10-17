package com.proximity.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"
    private const val KEYSTORE_ALIAS = "Proximity_db_master_key"
    internal const val PREFS_NAME = "Proximity_db_key"
    internal const val PREF_WRAPPED = "wrapped_passphrase"
    internal const val PREF_IV = "wrap_iv"
    private const val GCM_TAG_BITS = 128
    private const val PASSPHRASE_BYTES = 32

    internal class WrappedBlob(val sealed: ByteArray, val iv: ByteArray)

    internal interface Wrapper {
        @Throws(GeneralSecurityException::class)
        fun wrap(plain: ByteArray): WrappedBlob

        @Throws(GeneralSecurityException::class)
        fun unwrap(sealed: ByteArray, iv: ByteArray): ByteArray

        fun discardKey()
    }
    fun getOrCreatePassphrase(context: Context, wrapper: Wrapper = KeystoreWrapper): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(PREF_WRAPPED, null)
        val iv = prefs.getString(PREF_IV, null)
        if (wrapped != null && iv != null) {
            val existing = recoverable("passphrase unwrap") {
                String(
                    wrapper.unwrap(
                        Base64.decode(wrapped, Base64.NO_WRAP),
                        Base64.decode(iv, Base64.NO_WRAP)
                    ),
                    Charsets.UTF_8
                )
            }
            if (existing != null) return existing
            prefs.edit().remove(PREF_WRAPPED).remove(PREF_IV).apply()
            discardQuietly(wrapper)
        }

        val raw = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val passphrase = Base64.encodeToString(raw, Base64.NO_WRAP)

        val plain = passphrase.toByteArray(Charsets.UTF_8)
        val blob = recoverable("passphrase wrap") { wrapper.wrap(plain) }
            ?: run {
                discardQuietly(wrapper)
                recoverable("passphrase wrap retry") { wrapper.wrap(plain) }
            }

        if (blob != null) {
            prefs.edit()
                .putString(PREF_WRAPPED, Base64.encodeToString(blob.sealed, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(blob.iv, Base64.NO_WRAP))
                .apply()
        } else {
            Log.e(TAG, "Keystore unusable - passphrase not persisted, history will not survive restart")
        }
        return passphrase
    }

    private fun <T> recoverable(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "$what failed (${e.javaClass.simpleName}) - regenerating key material", e)
            null
        } catch (e: ProviderException) {
            Log.w(TAG, "$what failed in the Keystore provider - regenerating key material", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "$what failed reading the Keystore - regenerating key material", e)
            null
        }

    private fun discardQuietly(wrapper: Wrapper) {
        runCatching { wrapper.discardKey() }
            .onFailure { Log.w(TAG, "Discarding stale wrapping key failed: ${it.message}") }
    }

    private object KeystoreWrapper : Wrapper {

        override fun wrap(plain: ByteArray): WrappedBlob {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey())
            return WrappedBlob(sealed = cipher.doFinal(plain), iv = cipher.iv)
        }

        override fun unwrap(sealed: ByteArray, iv: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(sealed)
        }

        override fun discardKey() {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        }

        private fun masterKey(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            try {
                (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
            } catch (e: UnrecoverableKeyException) {
                // Entry exists but can't be loaded (invalidated) replace it.
                Log.w(TAG, "Master key unrecoverable - regenerating", e)
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }
    }
}
