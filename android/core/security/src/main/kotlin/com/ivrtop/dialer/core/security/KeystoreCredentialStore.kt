package com.ivrtop.dialer.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import com.ivrtop.dialer.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File layout: noBackupFilesDir/vault/<sha256(key)>.bin  (never included in Auto Backup / D2D)
 * Blob format: [1 byte version][12 bytes IV][ciphertext+tag]; AAD = logical key.
 */
@Singleton
class KeystoreCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SecureCredentialStore {

    private val mutex = Mutex()
    private val dir: File by lazy { File(context.noBackupFilesDir, "vault").apply { mkdirs() } }
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    override suspend fun put(key: String, secret: String) = withContext(io) {
        mutex.withLock {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey())
            cipher.updateAAD(key.toByteArray())
            val ct = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
            val blob = byteArrayOf(VERSION) + cipher.iv + ct
            val tmp = File(dir, fileName(key) + ".tmp")
            tmp.writeBytes(blob)
            if (!tmp.renameTo(File(dir, fileName(key)))) {
                File(dir, fileName(key)).writeBytes(blob)
                tmp.delete()
            }
            index(add = key)
        }
    }

    override suspend fun get(key: String): String? = withContext(io) {
        mutex.withLock {
            val f = File(dir, fileName(key))
            if (!f.exists()) return@withLock null
            try {
                val blob = f.readBytes()
                require(blob.isNotEmpty() && blob[0] == VERSION) { "unknown vault version" }
                val iv = blob.copyOfRange(1, 1 + IV_LEN)
                val ct = blob.copyOfRange(1 + IV_LEN, blob.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
                cipher.updateAAD(key.toByteArray())
                String(cipher.doFinal(ct), Charsets.UTF_8)
            } catch (e: Exception) {
                // Key invalidated (e.g. device restore to new hardware) – secret is unrecoverable.
                Timber.w("Vault entry unreadable (${e.javaClass.simpleName}); removing")
                f.delete()
                null
            }
        }
    }

    override suspend fun remove(key: String) = withContext(io) {
        mutex.withLock {
            File(dir, fileName(key)).delete()
            index(remove = key)
        }
    }

    override suspend fun removeByPrefix(prefix: String) = withContext(io) {
        mutex.withLock {
            readIndex().filter { it.startsWith(prefix) }.forEach {
                File(dir, fileName(it)).delete()
                index(remove = it)
            }
        }
    }

    override suspend fun clearAll() = withContext(io) {
        mutex.withLock {
            dir.listFiles()?.forEach { it.delete() }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    // ---- index of logical keys (names only, no secrets) so prefix removal works ----
    private val indexFile get() = File(dir, "index.lst")
    private fun readIndex(): Set<String> = if (indexFile.exists()) indexFile.readLines().filter { it.isNotBlank() }.toSet() else emptySet()
    private fun index(add: String? = null, remove: String? = null) {
        val set = readIndex().toMutableSet()
        add?.let(set::add); remove?.let(set::remove)
        indexFile.writeText(set.joinToString("\n"))
    }

    private fun fileName(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) + ".bin"
    }

    private fun masterKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generateKey(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            // Must stay usable while locked: incoming calls register while the screen is off.
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return try {
            gen.init(spec)
            gen.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            generateKey(strongBox = false)
        } catch (e: Exception) {
            if (strongBox) generateKey(strongBox = false) else throw e
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ivrtop_vault_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val IV_LEN = 12
    }
}
