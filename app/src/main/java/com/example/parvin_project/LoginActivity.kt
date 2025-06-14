// LoginActivity.kt
package com.example.parvin_project

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize TokenManager here, before any API calls or token checks
        // This ensures SharedPreferences is ready.
        RetrofitClient.initialize(applicationContext) // Initialize TokenManager through RetrofitClient

        // Check if a token already exists
        if (TokenManager.hasAccessToken()) {
            Log.d("LoginActivity", "Access token found. Bypassing login.")
            navigateToMainActivity()
            return // Prevent further execution of onCreate
        }

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isBlank() || password.isBlank()) {
            errorTextView.text = "نام کاربری و رمز عبور نمی‌توانند خالی باشند."
            errorTextView.visibility = View.VISIBLE
            return
        }

        loginButton.isEnabled = false
        errorTextView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = UserLogin(username, password)
                val response = RetrofitClient.apiService.login(request)

                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true // Re-enable button

                    if (response.isSuccessful) {
                        val loginResponse = response.body()
                        if (loginResponse != null) {
                            // Login successful, save tokens
                            TokenManager.saveTokens(loginResponse.token)
                            Log.d("LoginActivity", "Login successful! Token saved.")
                            Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز بود.", Toast.LENGTH_SHORT).show()
                            navigateToMainActivity()
                        } else {
                            // Should not happen if 200 OK is received with a body
                            errorTextView.text = "خطا: پاسخ سرور نامعتبر است."
                            errorTextView.visibility = View.VISIBLE
                            Log.e("LoginActivity", "Login successful but response body is null.")
                        }
                    } else {
                        // Login failed with an HTTP error
                        Log.e("LoginActivity", "Login failed. Code: ${response.code()}, Message: ${response.message()}")
                        // As per requirement, show "نام کاربری یا رمز عبور اشتباه است." for any non-200 response
                        errorTextView.text = "نام کاربری یا رمز عبور اشتباه است."
                        errorTextView.visibility = View.VISIBLE
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true
                    Log.e("LoginActivity", "HTTP Exception during login: ${e.message()}", e)
                    errorTextView.text = "نام کاربری یا رمز عبور اشتباه است." // Also show this for HTTP exceptions
                    errorTextView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true
                    Log.e("LoginActivity", "Error during login: ${e.message}", e)
                    errorTextView.text = "خطا در اتصال به سرور. لطفا اتصال اینترنت را بررسی کنید."
                    errorTextView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close LoginActivity so user can't go back to it via back button
    }
}