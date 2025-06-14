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
import retrofit2.HttpException // Add this import

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

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

        // Disable button to prevent multiple clicks during login attempt
        loginButton.isEnabled = false
        errorTextView.visibility = View.GONE // Hide previous errors

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = UserLogin(username, password)
                val response = RetrofitClient.apiService.login(request)

                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true // Re-enable button

                    if (response.isSuccessful) {
                        // Login successful (200 OK)
                        Log.d("LoginActivity", "Login successful!")
                        Toast.makeText(this@LoginActivity, "ورود موفقیت‌آمیز بود.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish() // Close LoginActivity
                    } else {
                        // Login failed with an HTTP error
                        Log.e("LoginActivity", "Login failed. Code: ${response.code()}, Message: ${response.message()}")
                        if (response.code() == 422) {
                            errorTextView.text = "نام کاربری یا رمز عبور اشتباه است."
                            errorTextView.visibility = View.VISIBLE
                        } else {
                            // Handle other error codes if necessary
                            errorTextView.text = "خطا در ورود: ${response.code()} ${response.message()}"
                            errorTextView.visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true // Re-enable button
                    Log.e("LoginActivity", "HTTP Exception during login: ${e.message()}", e)
                    // You can parse e.response()?.errorBody()?.string() for more detailed error from server if needed
                    errorTextView.text = "خطای شبکه: ${e.code()} ${e.message()}"
                    errorTextView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loginButton.isEnabled = true // Re-enable button
                    Log.e("LoginActivity", "Error during login: ${e.message}", e)
                    errorTextView.text = "خطا در اتصال به سرور. لطفا اتصال اینترنت را بررسی کنید."
                    errorTextView.visibility = View.VISIBLE
                }
            }
        }
    }
}