package rem.receiver.android

import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object RemCrypto {
	private const val KEY_BYTES = 32
	private const val FINGERPRINT_BYTES = 8
	const val NONCE_BYTES = 12
	const val TAG_BYTES = 16
	private const val PBKDF2_ITERATIONS = 100000

	private val KEY_SALT = "RemSound.v1.audio-key".toByteArray(StandardCharsets.UTF_8)
	private val FINGERPRINT_SALT = "RemSound.v1.fingerprint".toByteArray(StandardCharsets.UTF_8)
	private val EMPTY_KEY = byteArrayOf(
		-25, -2, -108, -23, 109, 124, -6, 108, 81, -70, -114, 21, -112, -11, 14, 55,
		-30, 52, -27, -80, -77, -26, 98, -78, 75, -28, -118, 66, 97, -11, -100, 24
	)
	private val EMPTY_FINGERPRINT = byteArrayOf(122, 120, -30, -40, 16, 21, 75, -9)

	fun deriveKey(password: String): ByteArray {
		if (password.isEmpty()) return EMPTY_KEY
		val spec = PBEKeySpec(password.toCharArray(), KEY_SALT, PBKDF2_ITERATIONS, 256)
		val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
		return factory.generateSecret(spec).encoded
	}

	fun fingerprint(password: String): ByteArray {
		if (password.isEmpty()) return EMPTY_FINGERPRINT
		val spec = PBEKeySpec(password.toCharArray(), FINGERPRINT_SALT, PBKDF2_ITERATIONS, 64)
		val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
		return factory.generateSecret(spec).encoded
	}

	fun tryDecrypt(key: ByteArray, packet: ByteArray, offset: Int, length: Int): ByteArray? {
		if (length < NONCE_BYTES + TAG_BYTES) return null
		val nonce = packet.copyOfRange(offset, offset + NONCE_BYTES)
		val tag = packet.copyOfRange(offset + NONCE_BYTES, offset + NONCE_BYTES + TAG_BYTES)
		val ciphertext = packet.copyOfRange(offset + NONCE_BYTES + TAG_BYTES, offset + length)
		
		val input = ByteArray(ciphertext.size + tag.size)
		System.arraycopy(ciphertext, 0, input, 0, ciphertext.size)
		System.arraycopy(tag, 0, input, ciphertext.size, tag.size)

		return try {
			val cipher = Cipher.getInstance("AES/GCM/NoPadding")
			val keySpec = SecretKeySpec(key, "AES")
			val gcmSpec = GCMParameterSpec(128, nonce)
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
			cipher.doFinal(input)
		} catch (e: Exception) {
			e.printStackTrace()
			null
		}
	}
}
