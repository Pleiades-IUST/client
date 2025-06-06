// LoginActivity.kt
package com.example.parvin_project

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView

    // Mock credentials
    private val MOCK_USERNAME = "user"
    private val MOCK_PASSWORD = "password"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Hide the default ActionBar for the login screen
        supportActionBar?.hide()

        // Initialize UI components
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val username = usernameEditText.text.toString().trim()
        val password = passwordEditText.text.toString().trim()

        if (username == MOCK_USERNAME && password == MOCK_PASSWORD) {
            // Successful login
            errorTextView.visibility = TextView.GONE // Hide any previous error
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Finish LoginActivity so user cannot go back to it
        } else {
            // Failed login
            errorTextView.text = "Invalid username or password. Please try again."
            errorTextView.visibility = TextView.VISIBLE
        }
    }
}
