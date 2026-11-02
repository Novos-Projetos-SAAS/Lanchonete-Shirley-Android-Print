package com.lanchoneteshirley.impressao.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("print_secret", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun saveValue(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        preferences.edit().putString(name, Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)).commit()
    }
    private fun loadValue(name: String): String? = runCatching {
        val payload = Base64.decode(preferences.getString(name, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, IV_SIZE))) }
        String(cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)), Charsets.UTF_8)
    }.getOrNull()
    fun save(token: String) = saveValue("credential", token)
    fun load(): String? = loadValue("credential")
    fun savePendingLease(jobId: String, leaseToken: String) = saveValue("pending_lease", "$jobId\n$leaseToken")
    fun loadPendingLease(jobId: String): String? = loadValue("pending_lease")?.split('\n', limit = 2)?.takeIf { it.size == 2 && it[0] == jobId }?.get(1)
    fun clearPendingLease() { preferences.edit().remove("pending_lease").commit() }
    fun clear() { preferences.edit().clear().apply() }
    private companion object { const val KEY_ALIAS = "shirley_print_device_credential"; const val TRANSFORMATION = "AES/GCM/NoPadding"; const val IV_SIZE = 12 }
}
