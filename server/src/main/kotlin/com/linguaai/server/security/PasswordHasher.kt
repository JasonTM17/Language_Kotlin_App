package com.linguaai.server.security

import at.favre.lib.crypto.bcrypt.BCrypt

/** Password hashing boundary. Plain-text passwords never leave this class. */
object PasswordHasher {

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(plain: String): String = hasher.hashToString(12, plain.toCharArray())

    fun verify(plain: String, hash: String): Boolean =
        verifier.verify(plain.toCharArray(), hash.toByteArray()).verified
}
