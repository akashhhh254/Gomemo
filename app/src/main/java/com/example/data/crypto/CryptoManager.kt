package com.example.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object CryptoManager {
    private const val PREFS_NAME = "gomemo_e2ee_keys"
    private const val KEY_PRIVATE = "e2ee_private_key"
    private const val KEY_PUBLIC = "e2ee_public_key"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private var prefs: SharedPreferences? = null
    private var cachedKeyPair: KeyPair? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadOrGenerateKeyPair()
        }
    }

    private fun loadOrGenerateKeyPair(): KeyPair {
        cachedKeyPair?.let { return it }

        val privString = prefs?.getString(KEY_PRIVATE, null)
        val pubString = prefs?.getString(KEY_PUBLIC, null)

        if (!privString.isNullOrBlank() && !pubString.isNullOrBlank()) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privBytes = Base64.decode(privString, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubString, Base64.NO_WRAP)

                val privKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

                val pair = KeyPair(pubKey, privKey)
                cachedKeyPair = pair
                return pair
            } catch (e: Exception) {
                Log.e("CryptoManager", "Failed to load saved keypair, generating new: ${e.message}")
            }
        }

        // Generate new EC KeyPair (secp256r1 / NIST P-256)
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec, SecureRandom())
        val newPair = kpg.generateKeyPair()

        val privBase64 = Base64.encodeToString(newPair.private.encoded, Base64.NO_WRAP)
        val pubBase64 = Base64.encodeToString(newPair.public.encoded, Base64.NO_WRAP)

        prefs?.edit()
            ?.putString(KEY_PRIVATE, privBase64)
            ?.putString(KEY_PUBLIC, pubBase64)
            ?.apply()

        cachedKeyPair = newPair
        return newPair
    }

    fun getPublicKeyBase64(): String {
        val pair = cachedKeyPair ?: loadOrGenerateKeyPair()
        return Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
    }

    fun getPrivateKey(): PrivateKey {
        val pair = cachedKeyPair ?: loadOrGenerateKeyPair()
        return pair.private
    }

    private fun parsePublicKey(publicKeyBase64: String): PublicKey {
        val bytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(X509EncodedKeySpec(bytes))
    }

    /**
     * Derive shared 256-bit AES secret using ECDH Key Agreement + SHA-256 Digest
     */
    private fun deriveSharedSecretKey(peerPublicKeyBase64: String): SecretKey {
        val peerPublic = parsePublicKey(peerPublicKeyBase64)
        val myPrivate = getPrivateKey()

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivate)
        keyAgreement.doPhase(peerPublic, true)
        val sharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit symmetric AES key
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypt text payload with true End-to-End Encryption (AES-256-GCM)
     * Returns Pair<CiphertextBase64, NonceBase64>
     */
    fun encryptMessage(plaintext: String, recipientPublicKeyBase64: String): Pair<String, String> {
        val sharedKey = deriveSharedSecretKey(recipientPublicKeyBase64)
        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, spec)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Pair(ciphertextBase64, ivBase64)
    }

    /**
     * Decrypt text payload with true End-to-End Encryption (AES-256-GCM)
     */
    fun decryptMessage(ciphertextBase64: String, nonceBase64: String, senderPublicKeyBase64: String): String {
        return try {
            val sharedKey = deriveSharedSecretKey(senderPublicKeyBase64)
            val iv = Base64.decode(nonceBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, sharedKey, spec)

            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Decryption failed: ${e.message}")
            "[Encrypted Message]"
        }
    }

    /**
     * Encrypt raw photo bytes for E2EE private photo sharing
     * Returns: Triple<EncryptedBytes, EncryptedKeyBase64, NonceBase64>
     */
    fun encryptPhotoBytes(
        rawBytes: ByteArray,
        recipientPublicKeyBase64: String
    ): Triple<ByteArray, String, String> {
        // 1. Generate unique random AES-256 key for this photo
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val photoKey = keyGen.generateKey()

        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, photoKey, spec)

        val encryptedPhotoData = cipher.doFinal(rawBytes)

        // 2. Encrypt the photo key with ECDH shared secret
        val sharedKey = deriveSharedSecretKey(recipientPublicKeyBase64)
        val keyCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keyIv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        keyCipher.init(Cipher.ENCRYPT_MODE, sharedKey, GCMParameterSpec(GCM_TAG_LENGTH, keyIv))
        val encryptedKeyBytes = keyCipher.doFinal(photoKey.encoded)

        // Combine keyIv + encryptedKeyBytes
        val combinedKeyPayload = keyIv + encryptedKeyBytes
        val encryptedKeyBase64 = Base64.encodeToString(combinedKeyPayload, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        return Triple(encryptedPhotoData, encryptedKeyBase64, ivBase64)
    }

    /**
     * Decrypt encrypted photo bytes from E2EE private photo sharing
     */
    fun decryptPhotoBytes(
        encryptedPhotoData: ByteArray,
        encryptedKeyBase64: String,
        nonceBase64: String,
        senderPublicKeyBase64: String
    ): ByteArray? {
        return try {
            val sharedKey = deriveSharedSecretKey(senderPublicKeyBase64)
            val combinedKeyPayload = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP)
            val keyIv = combinedKeyPayload.copyOfRange(0, GCM_IV_LENGTH)
            val encKey = combinedKeyPayload.copyOfRange(GCM_IV_LENGTH, combinedKeyPayload.size)

            val keyCipher = Cipher.getInstance("AES/GCM/NoPadding")
            keyCipher.init(Cipher.DECRYPT_MODE, sharedKey, GCMParameterSpec(GCM_TAG_LENGTH, keyIv))
            val rawKeyBytes = keyCipher.doFinal(encKey)
            val photoKey = SecretKeySpec(rawKeyBytes, "AES")

            val iv = Base64.decode(nonceBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, photoKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            cipher.doFinal(encryptedPhotoData)
        } catch (e: Exception) {
            Log.e("CryptoManager", "Failed to decrypt photo: ${e.message}")
            null
        }
    }
}
