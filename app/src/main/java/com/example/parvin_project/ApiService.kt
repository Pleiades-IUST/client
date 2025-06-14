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

interface PleiadesApiService {

    @POST("/auth/login")
    // Modified to return Response<Unit> assuming an empty 200 OK body for success.
    // If your server actually returns a string like "OK", you might need Response<String>.
    // But typically for login, success is just the status code.
    suspend fun login(@Body request: UserLogin): Response<Unit> // <-- Changed from Response<String>

    @POST("/drive")
    suspend fun createDriveEntry(@Body request: DriveData): Response<Unit>
}

object RetrofitClient {

    // TODO: REPLACE WITH YOUR ACTUAL BASE URL
    private const val BASE_URL = "http://192.168.81.79:8080" // e.g., "http://192.168.1.100:8000" or "https://api.yourdomain.com"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
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
}