package dev.arny.githubmanager.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.arny.githubmanager.domain.isFreshCacheEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted on-disk API GET cache. No access tokens or private JSON in file names. */
class EncryptedResponseCache(context: Context) {
    @Serializable data class Entry(val savedAt: Long, val etag: String?, val body: String)

    private val dir = File(context.applicationContext.noBackupFilesDir, "github_api_cache_v1").apply { mkdirs() }
    private val json = Json
    private val alias = "github_manager_cache_aes_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }

    private fun digest(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun file(token: String, url: String): File = File(dir, digest("v1|$token|$url") + ".cache")

    @Synchronized fun get(token: String, url: String): Entry? = runCatching {
        val f = file(token, url)
        if (!f.isFile || f.length() > MAX_ENTRY_BYTES + 2048L) return@runCatching null
        val bytes = f.readBytes()
        if (bytes.size <= 12) return@runCatching null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val plaintext = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        json.decodeFromString<Entry>(String(plaintext, Charsets.UTF_8))
    }.getOrNull()

    @Synchronized fun put(token: String, url: String, body: String, etag: String?) {
        if (body.toByteArray(Charsets.UTF_8).size > MAX_ENTRY_BYTES) return
        runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val bytes = json.encodeToString(Entry(System.currentTimeMillis(), etag, body))
                .toByteArray(Charsets.UTF_8)
            val encrypted = cipher.iv + cipher.doFinal(bytes)
            val target = file(token, url)
            val temp = File(dir, "${target.name}.tmp")
            temp.writeBytes(encrypted)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) temp.delete()
            }
            trim()
        }
    }

    fun isFresh(entry: Entry): Boolean = isFreshCacheEntry(entry.savedAt, System.currentTimeMillis(), TTL_MILLIS)

    @Synchronized fun clear() { dir.listFiles()?.forEach { it.delete() } }

    private fun trim() {
        val files = (dir.listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }
        var bytes = 0L
        files.forEachIndexed { index, file ->
            bytes += file.length()
            if (index >= MAX_ENTRIES || bytes > MAX_TOTAL_BYTES) file.delete()
        }
    }

    companion object {
        const val TTL_MILLIS = 10 * 60 * 1000L
        private const val MAX_ENTRY_BYTES = 700 * 1024
        private const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
        private const val MAX_ENTRIES = 250
    }
}
