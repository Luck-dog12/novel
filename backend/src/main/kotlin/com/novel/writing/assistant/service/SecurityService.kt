package com.novel.writing.assistant.service

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object SecurityService {
    private const val ENCRYPTION_ALGORITHM = "AES"

    private fun getAppSecretKey(): String {
        return System.getenv("APP_SECRET_KEY")
            ?: throw IllegalStateException("APP_SECRET_KEY is not set")
    }

    private fun getKeySpec(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(getAppSecretKey().toByteArray())
        return SecretKeySpec(keyBytes, ENCRYPTION_ALGORITHM)
    }
    
    /**
     * Hash API key for storage
     * @param apiKey Original API key
     * @return Hashed API key
     */
    fun hashApiKey(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(apiKey.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
    
    /**
     * Verify API key against hashed value
     * @param apiKey Original API key
     * @param hashedApiKey Hashed API key
     * @return True if API key is valid
     */
    fun verifyApiKey(apiKey: String, hashedApiKey: String): Boolean {
        return hashApiKey(apiKey) == hashedApiKey
    }
    
    /**
     * Encrypt data
     * @param data Data to encrypt
     * @return Encrypted data as Base64 string
     */
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val keySpec = getKeySpec()
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        val encrypted = cipher.doFinal(data.toByteArray())
        return Base64.getEncoder().encodeToString(encrypted)
    }
    
    /**
     * Decrypt data
     * @param encryptedData Encrypted data as Base64 string
     * @return Decrypted data
     */
    fun decrypt(encryptedData: String): String {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val keySpec = getKeySpec()
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        val decoded = Base64.getDecoder().decode(encryptedData)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted)
    }
    
    /**
     * Generate secure random token
     * @param length Token length
     * @return Secure random token
     */
    fun generateToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
            .take(length)
    }
    
    /**
     * Validate API key format
     * @param apiKey API key to validate
     * @return True if API key format is valid
     */
    fun validateApiKeyFormat(apiKey: String): Boolean {
        // Basic API key format validation
        return apiKey.length in 32..64 && apiKey.matches(Regex("^[a-zA-Z0-9_-]+"))
    }
}
