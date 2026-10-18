package dev.arny.githubmanager.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.arny.githubmanager.remote.DraftBundle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted app-private drafts; no backup; account/repo/ref separated. */
class EncryptedDraftStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "remote_git_drafts_v1").apply { mkdirs() }
    private val alias = "ghm_remote_drafts_v1"
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    }

    private fun file(token: String, repoId: Long, branch: String): File {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("v1|$token|$repoId|$branch".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$fingerprint.enc")
    }

    @Synchronized fun read(token: String, repoId: Long, branch: String): DraftBundle = runCatching {
        val bytes = file(token, repoId, branch).readBytes()
        require(bytes.size in 29..(16 * 1024 * 1024))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        json.decodeFromString<DraftBundle>(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
    }.getOrDefault(DraftBundle())

    @Synchronized fun write(token: String, repoId: Long, branch: String, value: DraftBundle) {
        val dest = file(token, repoId, branch)
        if (value.changes.isEmpty() && value.editor == null) { dest.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val data = cipher.iv + cipher.doFinal(json.encodeToString(value).toByteArray())
        require(data.size <= 16 * 1024 * 1024) { "Превышен лимит черновиков" }
        val temp = File(dir, dest.name + ".tmp")
        temp.writeBytes(data)
        if (!temp.renameTo(dest)) {
            dest.delete()
            check(temp.renameTo(dest)) { "Не удалось сохранить черновик" }
        }
    }

    @Synchronized fun clearAll() { dir.listFiles()?.forEach { it.delete() } }
}
