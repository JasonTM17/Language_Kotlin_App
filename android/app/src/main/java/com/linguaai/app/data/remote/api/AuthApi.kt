package com.linguaai.app.data.remote.api

import com.linguaai.app.data.remote.dto.AuthResponseDto
import com.linguaai.app.data.remote.dto.LoginRequestDto
import com.linguaai.app.data.remote.dto.LogoutRequestDto
import com.linguaai.app.data.remote.dto.ProfileDto
import com.linguaai.app.data.remote.dto.RefreshRequestDto
import com.linguaai.app.data.remote.dto.RefreshResponseDto
import com.linguaai.app.data.remote.dto.RegisterRequestDto
import com.linguaai.app.data.remote.dto.UpdateProfileRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequestDto): Response<AuthResponseDto>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequestDto): Response<AuthResponseDto>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): Response<RefreshResponseDto>

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequestDto): Response<Map<String, Boolean>>

    @GET("profile")
    suspend fun profile(): Response<ProfileDto>

    @PUT("profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): Response<ProfileDto>
}
