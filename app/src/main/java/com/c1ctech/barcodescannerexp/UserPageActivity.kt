package com.c1ctech.barcodescannerexp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import com.google.firebase.firestore.FieldValue

class UserPageActivity : AppCompatActivity() {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_page)
        database = FirebaseDatabase.getInstance().getReference("App/Touchscreen/Commands/")

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Retrieve data from Intent
        val userId = intent.getStringExtra("USER_ID") ?: "No id provided"
        val fullName = intent.getStringExtra("FULL_NAME") ?: "No fullname provided"

        val userPoints = intent.getIntExtra("USER_POINTS", 0)
        val userLargeCount = intent.getIntExtra("USER_LARGE_BOTTLE_COUNT", 0)
        val userSmallCount = intent.getIntExtra("USER_SMALL_BOTTLE_COUNT", 0)

        // Find TextViews and set the received data
        val emailTextView: TextView = findViewById(R.id.emailTextView)
        val pointsTextView: TextView = findViewById(R.id.textView)
        val bottleCountTextView: TextView = findViewById(R.id.textView2)
        val smallBottleCountTextView: TextView = findViewById(R.id.textView3) // Add a new TextView for small bottles

        emailTextView.text = fullName
        pointsTextView.text = userPoints.toString()
        bottleCountTextView.text = userLargeCount.toString()
        smallBottleCountTextView.text = userSmallCount.toString()
        // Set smallBottleCount to the UI



        // Push data to Firestore dynamically
        pushDataToFirestore(userId, userPoints, userLargeCount, userSmallCount)

        updateUserPoints(userId, userPoints)

        val backButton = findViewById<Button>(R.id.back)

        // Set up an OnClickListener for the button
        backButton.setOnClickListener {
            val intent = Intent(this, HomePage::class.java)
            addResetCommand()

            finish()
        }
    }

    private fun addResetCommand() {
        // Set the command directly without generating a unique ID
        database.child("command").setValue("RESET")
        // Write the command directly to the specified path
    }

    private fun pushDataToFirestore(userId: String, points: Int, largeBottleCount: Int, smallBottleCount: Int) {
        val totalBottles = largeBottleCount + smallBottleCount

        val data = mapOf(
            "action" to "points added",
            "bigBottles" to largeBottleCount,
            "points" to points,
            "smallBottles" to smallBottleCount,
            "timestamp" to Timestamp(Date()),
            "totalBottles" to totalBottles,
            "userId" to userId
        )

        firestore.collection("pointsAddedHistory").add(data)
            .addOnSuccessListener {
                Log.d("UserPageActivity", "DocumentSnapshot successfully written!")
            }
            .addOnFailureListener { e ->
                Log.w("UserPageActivity", "Error writing document", e)
            }
    }
    private fun updateUserPoints(userId: String, points: Int) {
        val userPointsRef = firestore.collection("userPoints").document(userId)

        // Increment the points field by the value of pointsToAdd
        userPointsRef.update(
            mapOf(
                "user_points_id" to userId,
                "points" to FieldValue.increment(points.toLong()),
                "updatedAt" to Timestamp(Date())
            )
        )
            .addOnSuccessListener {
                Log.d("UserPageActivity", "User points successfully updated!")
            }
            .addOnFailureListener { e ->
                Log.w("UserPageActivity", "Error updating user points", e)
            }
    }
}
