// TokenManager.kt
package com.example.parvin_project

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object TokenManager {

    private const val PREFS_NAME = "app_auth_prefs"
    private const val ACCESS_TOKEN_KEY = "access_token"
    private const val TOKEN_TYPE_KEY = "token_type"

    private lateinit var sharedPreferences: SharedPreferences

    // Call this once in your Application class or MainActivity's onCreate
    fun initialize(context: Context) {
        if (!this::sharedPreferences.isInitialized) {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            Log.d("TokenManager", "TokenManager initialized.")
        }
    }

    fun saveTokens(accessToken: String) {
        if (!this::sharedPreferences.isInitialized) {
            Log.e("TokenManager", "TokenManager not initialized. Call initialize(context) first!")
            return
        }
        with(sharedPreferences.edit()) {
            putString(ACCESS_TOKEN_KEY, accessToken)
            putString(TOKEN_TYPE_KEY, "bearer")
            apply() // Apply asynchronously
        }
        Log.d("TokenManager", "Access token saved.")
    }

    fun getAccessToken(): String? {
        if (!this::sharedPreferences.isInitialized) {
            Log.e("TokenManager", "TokenManager not initialized. Call initialize(context) first!")
            return null
        }
        return sharedPreferences.getString(ACCESS_TOKEN_KEY, null)
    }

    fun getTokenType(): String? {
        if (!this::sharedPreferences.isInitialized) {
            Log.e("TokenManager", "TokenManager not initialized. Call initialize(context) first!")
            return null
        }
        return sharedPreferences.getString(TOKEN_TYPE_KEY, null)
    }

    fun clearTokens() {
        if (!this::sharedPreferences.isInitialized) {
            Log.e("TokenManager", "TokenManager not initialized. Call initialize(context) first!")
            return
        }
        with(sharedPreferences.edit()) {
            remove(ACCESS_TOKEN_KEY)
            remove(TOKEN_TYPE_KEY)
            apply()
        }
        Log.d("TokenManager", "Tokens cleared.")
    }

    fun hasAccessToken(): Boolean {
        return getAccessToken() != null
    }
}