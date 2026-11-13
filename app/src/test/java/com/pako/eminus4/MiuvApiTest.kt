package com.pako.eminus4

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class MiuvApiTest {

    companion object {
        const val CRED_KEY = "30B6B13F-A4B8"
        const val SCVE_KEY = "9DBC85BD-D8F6-4EAA-804B-CE62B544B733"
    }

    private fun createKey(key: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
        val spec = PBEKeySpec(key.toCharArray(), salt, 1000, 384)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val encoded = factory.generateSecret(spec).encoded
        val aesKey = ByteArray(32)
        val iv = ByteArray(16)
        System.arraycopy(encoded, 0, aesKey, 0, 32)
        System.arraycopy(encoded, 32, iv, 0, 16)
        return Pair(aesKey, iv)
    }

    private fun encryptData(plainText: String, key: String): String {
        val salt = ByteArray(32)
        SecureRandom().nextBytes(salt)
        val (aesKey, iv) = createKey(key, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val result = ByteArray(salt.size + cipherBytes.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(cipherBytes, 0, result, salt.size, cipherBytes.size)
        return Base64.getEncoder().encodeToString(result)
    }

    private fun decryptData(b64Text: String, key: String): String {
        val decoded = Base64.getDecoder().decode(b64Text)
        val salt = ByteArray(32)
        val cipherBytes = ByteArray(decoded.size - 32)
        System.arraycopy(decoded, 0, salt, 0, 32)
        System.arraycopy(decoded, 32, cipherBytes, 0, cipherBytes.size)
        val (aesKey, iv) = createKey(key, salt)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    @Test
    fun testRecuperaInfoDocQR() {
        val matricula = "S24022198"
        val payload = "MAT=$matricula&TDOC=CRE&SCVE=$SCVE_KEY"
        println("Payload: $payload")
        val encryptedPayload = encryptData(payload, CRED_KEY)
        println("Encrypted sEncript header: $encryptedPayload")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val url = "https://dsia.uv.mx/WebApi/General/ApiDocUV/Servicios/RecuperaInfoDocQR"
        
        println("--- Testing GET on $url ---")
        try {
            val reqGet = Request.Builder()
                .url(url)
                .header("sEncript", encryptedPayload)
                .get()
                .build()
            val resp = client.newCall(reqGet).execute()
            val body = resp.body?.string() ?: ""
            println("GET Status: ${resp.code}, Body length: ${body.length}")
            println("RAW BODY: $body")
            println("Final URL: ${resp.request.url}")

            // Also test POST
            println("--- Testing POST ---")
            val reqPost = Request.Builder()
                .url(url)
                .header("sEncript", encryptedPayload)
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            val respPost = client.newCall(reqPost).execute()
            val bodyPost = respPost.body?.string() ?: ""
            println("POST Status: ${respPost.code}, Body: $bodyPost")
            if (respPost.isSuccessful) {
                val json = com.google.gson.JsonParser.parseString(bodyPost).asJsonObject
                val content = json.get("Content").asString
                val decrypted = decryptData(content, CRED_KEY)
                println(">>> DECRYPTED RESPONSE POST: $decrypted")
            }
            if (resp.isSuccessful) {
                println("RAW BODY: $body")
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                val content = json.get("Content").asString
                val decrypted = decryptData(content, CRED_KEY)
                println(">>> DECRYPTED RESPONSE: $decrypted")
            }
        } catch (e: Exception) {
            println("GET Exception: $e")
            e.printStackTrace()
        }
    }
}
