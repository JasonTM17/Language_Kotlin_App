package com.linguaai.app.domain.usecase

import com.linguaai.app.domain.model.AppError
import com.linguaai.app.domain.model.AppResult
import com.linguaai.app.domain.model.User
import com.linguaai.app.domain.repository.AuthRepository
import com.linguaai.app.domain.validation.Validators
import javax.inject.Inject

/** Signs the learner in after validating the credentials shape. */
class LoginUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        suspend operator fun invoke(
            email: String,
            password: String,
        ): AppResult<User> {
            Validators.validateEmail(email)?.let {
                return AppResult.Failure(AppError.Validation(field = "email", reason = it))
            }
            Validators.validatePassword(password)?.let {
                return AppResult.Failure(AppError.Validation(field = "password", reason = it))
            }
            return authRepository.login(email.trim().lowercase(), password)
        }
    }

/** Registers a new learner account after validating the input shape. */
class RegisterUseCase
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) {
        suspend operator fun invoke(
            email: String,
            username: String,
            password: String,
        ): AppResult<User> {
            Validators.validateEmail(email)?.let {
                return AppResult.Failure(AppError.Validation(field = "email", reason = it))
            }
            Validators.validateUsername(username)?.let {
                return AppResult.Failure(AppError.Validation(field = "username", reason = it))
            }
            Validators.validatePassword(password)?.let {
                return AppResult.Failure(AppError.Validation(field = "password", reason = it))
            }
            return authRepository.register(email.trim().lowercase(), username.trim(), password)
        }
    }
