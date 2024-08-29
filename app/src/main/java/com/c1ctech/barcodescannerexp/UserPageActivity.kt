package com.c1ctech.barcodescannerexp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class  UserPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_page)

        val userEmail = intent.getStringExtra("USER_EMAIL")
        val emailTextView: TextView = findViewById(R.id.emailTextView)
        emailTextView.text = userEmail ?: "No email provided"
    }
}
