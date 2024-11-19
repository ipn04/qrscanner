package com.c1ctech.barcodescannerexp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HomePage : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    private lateinit var cdatabase: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        database = FirebaseDatabase.getInstance().getReference("App/Touchscreen/Data")
        cdatabase = FirebaseDatabase.getInstance().getReference("App/Touchscreen/Commands");

        // Find the TextViews by their IDs
        val smallBottlesValue = findViewById<TextView>(R.id.smallBottlesValue)
        val bigBottlesValue = findViewById<TextView>(R.id.bigBottlesValue)
        val totalPointsValue = findViewById<TextView>(R.id.totalPointsValue)
        val smallBottlesPoints = findViewById<TextView>(R.id.smallPoints)
        val bigBottlesPoints = findViewById<TextView>(R.id.bigPoints)
        val totalBottles = findViewById<TextView>(R.id.totalBottle)
        val indicatorValue = findViewById<TextView>(R.id.textView10)

        // Set up a listener to read data
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Check if data exists
                if (dataSnapshot.exists()) {


                    if (dataSnapshot.hasChild("indicator")) {
                        indicatorValue.text = "[MACHINE READY] You can now insert your bottle"
                        val bigBottles = dataSnapshot.child("bigBottles").getValue(String::class.java) ?: 0
                        val smallBottles = dataSnapshot.child("smallBottles").getValue(String::class.java) ?: 0
                        val totalPoints = dataSnapshot.child("totalPoints").getValue(String::class.java) ?: 0

                        // Update the TextViews
                        smallBottlesValue.text = "$smallBottles"
                        bigBottlesValue.text = "$bigBottles"
                        totalPointsValue.text = "$totalPoints"

                        // Calculate and update points based on the number of bottles
                        val smallBottlesPointValue = smallBottles.toString().toInt() * 3 // Assuming 1 point per small bottle
                        val bigBottlesPointValue = bigBottles.toString().toInt() * 5   // Assuming 2 points per big bottle
                        val totalBottlesValue = smallBottles.toString().toInt() + bigBottles.toString().toInt()


                        smallBottlesPoints.text = "$smallBottlesPointValue"
                        bigBottlesPoints.text = "$bigBottlesPointValue"
                        totalBottles.text = "$totalBottlesValue"

                    } else{
                        indicatorValue.text = ""
                        smallBottlesValue.text = ""
                        bigBottlesValue.text = ""
                        totalPointsValue.text = ""
                        smallBottlesPoints.text = ""
                        bigBottlesPoints.text = ""
                        totalBottles.text = ""
                    }

                    if (dataSnapshot.hasChild("error")) {
                        val errorType = dataSnapshot.child("error").getValue(String::class.java)
                        if (!errorType.isNullOrEmpty()) {
                            handleError(errorType, database)
                        }
                    }

                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Handle error
                println("Database error: ${databaseError.message}")
            }
        })

        // Find the button by its ID
        val redeemButton = findViewById<Button>(R.id.button)

        // Set up an OnClickListener for the button
        redeemButton.setOnClickListener {

            cdatabase.child("command").removeValue()
            // Create an Intent to start MainActivity
            val intent = Intent(this, MainActivity::class.java)
            // Pass the text values to MainActivity


            intent.putExtra("smallBottles", smallBottlesValue.text.toString())
            intent.putExtra("bigBottles", bigBottlesValue.text.toString())
            intent.putExtra("totalPoints", totalPointsValue.text.toString())
            startActivity(intent)
        }
    }

    private fun handleError(errorType: String?, errorDatabase: DatabaseReference) {
        val message = when (errorType) {
            "weight" -> "The item is too heavy."
            "aluminum" -> "Aluminum is not allowed."
            "transparent" -> "The machine only accepts transparent bottle."

            else -> "An unknown error occurred."
        }
        showErrorDialog(message, errorDatabase)
    }


    private fun showErrorDialog(message: String?, errorDatabase: DatabaseReference) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                // Delete the error from the database
                errorDatabase.child("error").removeValue()
                dialog.dismiss()
            }
            .show()
    }

}
