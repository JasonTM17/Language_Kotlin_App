package com.linguaai.server.repository

import com.linguaai.server.api.ApiException
import com.linguaai.server.api.ErrorCodes
import com.linguaai.server.api.dto.ProfileDto
import com.linguaai.server.api.dto.UpdateProfileRequest
import com.linguaai.server.api.dto.UserDto
import com.linguaai.server.db.Languages
import com.linguaai.server.db.RefreshTokens
import com.linguaai.server.db.UserProfiles
import com.linguaai.server.db.Users
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/** Daily-goal fallback when the learner has not chosen one. */
private const val DEFAULT_DAILY_GOAL_MINUTES = 10

data class UserRecord(
    val id: Long,
    val email: String,
    val username: String,
    val passwordHash: String,
    val avatarUrl: String?,
)

data class RefreshTokenRecord(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val familyId: String,
    val expiresAt: LocalDateTime,
    val revoked: Boolean,
)

/** User + refresh-token persistence for the authentication flow. */
class AuthRepository {

    // ---- users ----

    fun createUser(email: String, username: String, passwordHash: String): UserDto = transaction {
        val normalized = email.trim().lowercase()
        val exists = Users.selectAll().andWhere { Users.email eq normalized }.any()
        if (exists) {
            throw ApiException(HttpStatusCode.Conflict, ErrorCodes.CONFLICT, "Email already registered")
        }
        val now = LocalDateTime.now()
        val id = Users.insert { row ->
            row[Users.email] = normalized
            row[Users.username] = username.trim()
            row[Users.passwordHash] = passwordHash
            row[Users.createdAt] = now
            row[Users.updatedAt] = now
        } get Users.id
        UserDto(id = id, email = normalized, username = username.trim())
    }

    fun findUserByEmail(email: String): UserRecord? = transaction {
        Users.selectAll().andWhere { Users.email eq email.trim().lowercase() }
            .firstOrNull()?.let(::toUserRecord)
    }

    fun findUserById(id: Long): UserRecord? = transaction {
        Users.selectAll().andWhere { Users.id eq id }
            .firstOrNull()?.let(::toUserRecord)
    }

    fun findUserDtoById(id: Long): UserDto? = transaction {
        Users.selectAll().andWhere { Users.id eq id }
            .firstOrNull()?.let { row ->
                UserDto(
                    id = row[Users.id],
                    email = row[Users.email],
                    username = row[Users.username],
                    avatarUrl = row[Users.avatarUrl],
                )
            }
    }

    // ---- refresh tokens ----

    fun saveRefreshToken(userId: Long, tokenHash: String, familyId: String, expiresAt: LocalDateTime): Long =
        transaction {
            RefreshTokens.insert { row ->
                row[RefreshTokens.userId] = userId
                row[RefreshTokens.tokenHash] = tokenHash
                row[RefreshTokens.familyId] = familyId
                row[RefreshTokens.expiresAt] = expiresAt
                row[RefreshTokens.createdAt] = LocalDateTime.now()
            } get RefreshTokens.id
        }

    fun findRefreshToken(tokenHash: String): RefreshTokenRecord? = transaction {
        RefreshTokens.selectAll().andWhere { RefreshTokens.tokenHash eq tokenHash }
            .firstOrNull()?.let { row ->
                RefreshTokenRecord(
                    id = row[RefreshTokens.id],
                    userId = row[RefreshTokens.userId],
                    tokenHash = row[RefreshTokens.tokenHash],
                    familyId = row[RefreshTokens.familyId],
                    expiresAt = row[RefreshTokens.expiresAt],
                    revoked = row[RefreshTokens.revoked],
                )
            }
    }

    fun revokeRefreshToken(id: Long) = transaction {
        RefreshTokens.update({ RefreshTokens.id eq id }) { row -> row[revoked] = true }
    }

    /** Reuse detection: a replayed token kills its whole family. */
    fun revokeFamily(familyId: String) = transaction {
        RefreshTokens.update({ RefreshTokens.familyId eq familyId }) { row -> row[revoked] = true }
    }

    // ---- profile ----

    fun findProfile(userId: Long): ProfileDto? = transaction {
        val user = Users.selectAll().andWhere { Users.id eq userId }.firstOrNull() ?: return@transaction null
        val profile = UserProfiles.selectAll().andWhere { UserProfiles.userId eq userId }.firstOrNull()
        ProfileDto(
            user = UserDto(
                id = user[Users.id],
                email = user[Users.email],
                username = user[Users.username],
                avatarUrl = user[Users.avatarUrl],
            ),
            languageId = profile?.get(UserProfiles.languageId),
            level = profile?.get(UserProfiles.level),
            goal = profile?.get(UserProfiles.goal),
            dailyGoalMinutes = profile?.get(UserProfiles.dailyGoalMinutes) ?: 10,
            onboarded = profile?.get(UserProfiles.onboarded) ?: false,
        )
    }

    fun updateProfile(userId: Long, request: UpdateProfileRequest): ProfileDto = transaction {
        request.languageId?.let { languageId ->
            val languageExists = Languages.selectAll().andWhere { Languages.id eq languageId }.any()
            if (!languageExists) {
                throw ApiException(HttpStatusCode.BadRequest, ErrorCodes.VALIDATION, "Unknown languageId")
            }
        }
        val existing = UserProfiles.selectAll().andWhere { UserProfiles.userId eq userId }.any()
        val now = LocalDateTime.now()
        if (existing) {
            UserProfiles.update({ UserProfiles.userId eq userId }) { row ->
                request.languageId?.let { row[UserProfiles.languageId] = it }
                request.level?.let { row[UserProfiles.level] = it }
                request.goal?.let { row[UserProfiles.goal] = it }
                request.dailyGoalMinutes?.let { row[UserProfiles.dailyGoalMinutes] = it }
                request.onboarded?.let { row[UserProfiles.onboarded] = it }
                row[UserProfiles.updatedAt] = now
            }
        } else {
            UserProfiles.insert { row ->
                row[UserProfiles.userId] = userId
                row[UserProfiles.languageId] = request.languageId
                row[UserProfiles.level] = request.level
                row[UserProfiles.goal] = request.goal
                row[UserProfiles.dailyGoalMinutes] = request.dailyGoalMinutes ?: DEFAULT_DAILY_GOAL_MINUTES
                row[UserProfiles.onboarded] = request.onboarded ?: false
                row[UserProfiles.updatedAt] = now
            }
        }
        findProfile(userId) ?: throw ApiException(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND, "User not found")
    }

    private fun toUserRecord(row: ResultRow) = UserRecord(
        id = row[Users.id],
        email = row[Users.email],
        username = row[Users.username],
        passwordHash = row[Users.passwordHash],
        avatarUrl = row[Users.avatarUrl],
    )
}
