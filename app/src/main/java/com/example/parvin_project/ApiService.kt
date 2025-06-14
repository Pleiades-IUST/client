// ApiService.kt
package com.example.parvin_project

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// Interface for your API endpoints
interface PleiadesApiService {

    @POST("/auth/login")
    suspend fun login(@Body request: UserLogin): Response<String> // Changed to String as per your doc "returns 'OK'"

    @POST("/drive")
    suspend fun createDriveEntry(@Body request: DriveData): Response<Unit> // Response<Unit> for 200 OK with empty body
}

// Singleton for Retrofit instance
object RetrofitClient {

    // TODO: REPLACE WITH YOUR ACTUAL BASE URL
    // IMPORTANT: Make sure this URL is correct and accessible.
    private const val BASE_URL = "http://192.168.81.79:8080" // e.g., "http://192.168.1.100:8000" or "https://api.yourdomain.com"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log request and response bodies
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // Add logging for debugging
        .connectTimeout(30, TimeUnit.SECONDS) // Connection timeout
        .readTimeout(30, TimeUnit.SECONDS)    // Read timeout
        .writeTimeout(30, TimeUnit.SECONDS)   // Write timeout
        .build()

    val apiService: PleiadesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PleiadesApiService::class.java)
    }
}