// MainActivity.kt
package com.example.parvin_project // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var helloButton: Button
    private lateinit var helloMessageTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the new Button and TextView from the layout
        helloButton = findViewById(R.id.helloButton)
        helloMessageTextView = findViewById(R.id.helloMessageTextView)

        // Set an OnClickListener for the button
        helloButton.setOnClickListener {
            // When the button is clicked, set the text of helloMessageTextView to "Hello!"
            helloMessageTextView.text = "Hello!"
        }
    }
}
