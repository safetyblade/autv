package dev.prestwich.autv.provider

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dev.prestwich.autv.data.provider.*
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Both session and bounded activation challenge are encrypted and excluded from backup. */
internal class EncryptedProviderSessionStore(
    context: Context,
    private val alias: String = "autv.9now.session.v1",
    fileName: String = "9now-session-v1",
) : ProviderSessionStore, ProviderActivationStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val activationFile = AtomicFile(File(context.noBackupFilesDir, "$fileName-activation"))
    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun readJson(target: AtomicFile): JSONObject? = try {
        val bytes = target.readFully()
        require(bytes.size in 29..65536)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    } catch (_: FileNotFoundException) { null }
    catch (_: Exception) { target.delete(); null }

    private fun writeJson(target: AtomicFile, json: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val output = target.startWrite()
        try { output.write(encrypted); target.finishWrite(output) }
        catch (error: Exception) { target.failWrite(output); throw error }
    }
    private fun privateContext(json: JSONObject): Map<String,String> {
        val extra = json.optJSONObject("context") ?: return emptyMap()
        return extra.keys().asSequence().associateWith { extra.get(it) as String }
    }
    override fun read(): ProviderSession? {
        val json = readJson(file) ?: return null
        return runCatching { ProviderSession(json.getString("access"), json.getString("refresh"), json.getLong("expires"), privateContext(json)) }
            .getOrElse { file.delete(); null }
    }
    override fun write(session: ProviderSession) {
        writeJson(file, JSONObject().put("access", session.accessToken).put("refresh", session.refreshToken).put("expires", session.expiresAt).apply { if (session.privateContext.isNotEmpty()) put("context", JSONObject(session.privateContext)) })
    }
    override fun readActivation(): DeviceActivation? {
        val json = readJson(activationFile) ?: return null
        return runCatching { DeviceActivation(json.getString("code"), json.getString("device"),
            json.getString("url"), json.getLong("expires"), json.getLong("interval"), privateContext(json)) }
            .getOrElse { activationFile.delete(); null }
    }
    override fun writeActivation(activation: DeviceActivation) {
        writeJson(activationFile, JSONObject().put("code", activation.userCode).put("device", activation.deviceCode)
            .put("url", activation.verificationUrl).put("expires", activation.expiresAt).put("interval", activation.pollIntervalMillis).apply { if (activation.privateContext.isNotEmpty()) put("context", JSONObject(activation.privateContext)) })
    }
    override fun clearActivation() { activationFile.delete() }
    override fun clear() {
        file.delete()
        activationFile.delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }
}

internal fun createNineNowProvider(context: Context): ProviderAdapter {
    val store = EncryptedProviderSessionStore(context)
    return NineNowAdapter(HttpNineNowTransport(), store, activationStore = store)
}
