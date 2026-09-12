package com.linguaai.server.security

import at.favre.lib.crypto.bcrypt.BCrypt

/** Password hashing boundary. Plain-text passwords never leave this class. */
object PasswordHasher {
    /**
     * BCrypt cost factor. 12 is roughly 250ms per hash on current hardware, which
     * is the usual recommendation. Raising it slows every login, so the value is
     * named and documented rather than left as a bare literal in the hash call.
     */
    private const val BCRYPT_COST = 12

    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(plain: String): String = hasher.hashToString(BCRYPT_COST, plain.toCharArray())

    fun verify(
        plain: String,
        hash: String,
    ): Boolean = verifier.verify(plain.toCharArray(), hash.toByteArray()).verified
}
