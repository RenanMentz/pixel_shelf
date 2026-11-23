package com.pixelshelf.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Hash de senha com salt aleatório (PBKDF2). */
object Senhas {

    private const val ITERACOES = 10_000
    private const val TAMANHO_BITS = 256

    fun gerarSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(senha: String, salt: String): String {
        val spec = PBEKeySpec(
            senha.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), ITERACOES, TAMANHO_BITS
        )
        val chave = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(chave, Base64.NO_WRAP)
    }

    /** Comparação em tempo constante. */
    fun confere(senha: String, salt: String, hashEsperado: String): Boolean =
        MessageDigest.isEqual(hash(senha, salt).toByteArray(), hashEsperado.toByteArray())
}
