// ApiService.kt
package com.example.parvin_project

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface PleiadesApiService {

    @POST("/auth/login")
    // Now returns LoginResponse
    suspend fun login(@Body request: UserLogin): Response<LoginResponse>

    @POST("/drive")
    // Removed @Header("Authorization") as it will be added by the interceptor
    suspend fun createDriveEntry(@Body request: DriveData): Response<Unit>
}

object RetrofitClient {

    // TODO: REPLACE WITH YOUR ACTUAL BASE URL
    private const val BASE_URL = "http://192.168.81.79:8080" // e.g., "http://192.168.1.100:8000" or "https://api.yourdomain.com"

    // Interceptor to add Authorization header
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = TokenManager.getAccessToken() // Get token from our TokenManager
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }
        chain.proceed(newRequest)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)     // Add the authentication interceptor FIRST
        .addInterceptor(loggingInterceptor) // Then add logging for debugging
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: PleiadesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PleiadesApiService::class.java)
    }

    // This function will be called once in your Application class or MainActivity
    // to initialize TokenManager with a Context
    fun initialize(context: android.content.Context) {
        TokenManager.initialize(context)
    }
}