package com.mailshelf.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

data class Account(val email: String, val label: String)
class Vault(context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val alias = "mailshelf-vault-v1"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)
    @Synchronized private fun read(): JSONArray {
        val raw = prefs.getString("sealed", null) ?: return JSONArray()
        val parts = raw.split(":")
        require(parts.size == 2)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        return JSONArray(String(c.doFinal(decode(parts[1])), Charsets.UTF_8))
    }
    private fun save(a: JSONArray) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val sealed = encode(c.iv) + ":" + encode(c.doFinal(a.toString().toByteArray(Charsets.UTF_8)))
        check(prefs.edit().putString("sealed", sealed).commit()) { "Could not save vault" }
    }
    @Synchronized fun accounts(): List<Account> {
        val a = read()
        return (0 until a.length()).map { a.getJSONObject(it).let { j -> Account(j.getString("email"), j.getString("label")) } }
    }
    @Synchronized fun password(email: String): String {
        val a = read()
        for (i in 0 until a.length()) {
            val j = a.getJSONObject(i)
            if (j.getString("email") == email) return j.getString("password")
        }
        error("Account not found")
    }
    @Synchronized fun put(account: Account, password: String) {
        val old = read()
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val j = old.getJSONObject(i)
            if (j.getString("email") != account.email) next.put(j)
        }
        next.put(JSONObject().put("email", account.email).put("label", account.label).put("password", password))
        save(next)
    }
    @Synchronized fun remove(email: String) {
        val old = read()
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val j = old.getJSONObject(i)
            if (j.getString("email") != email) next.put(j)
        }
        save(next)
    }
}
