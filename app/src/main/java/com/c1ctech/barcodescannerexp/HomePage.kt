package com.c1ctech.barcodescannerexp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class HomePage : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval: Long = 5000 // Update interval in milliseconds (e.g., 5 seconds)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home_page)

        // Find the TextViews by their IDs
        val smallBottlesValue = findViewById<TextView>(R.id.smallBottlesValue)
        val bigBottlesValue = findViewById<TextView>(R.id.bigBottlesValue)
        val totalPointsValue = findViewById<TextView>(R.id.totalPointsValue)


        // Start polling for data
        startPolling(smallBottlesValue, bigBottlesValue, totalPointsValue)

        // Find the button by its ID
        val redeemButton = findViewById<Button>(R.id.button)

        // Set up an OnClickListener for the button
        redeemButton.setOnClickListener {
            // Create an Intent to start MainActivity
            val intent = Intent(this, MainActivity::class.java)
            // Pass the text values to MainActivity
            intent.putExtra("smallBottles", smallBottlesValue.text.toString())
            intent.putExtra("bigBottles", bigBottlesValue.text.toString())
            intent.putExtra("totalPoints", totalPointsValue.text.toString())
            startActivity(intent)
        }
    }

    private fun startPolling(
        smallBottlesValue: TextView,
        bigBottlesValue: TextView,
        totalPointsValue: TextView
    ) {
        handler.post(object : Runnable {
            override fun run() {
                fetchDataAndUpdateUI(smallBottlesValue, bigBottlesValue, totalPointsValue)
                // Schedule the next update
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun fetchDataAndUpdateUI(
        smallBottlesValue: TextView,
        bigBottlesValue: TextView,
        totalPointsValue: TextView
    ) {
        Thread {
            try {
                // Create a URL object
                val url = URL("http://192.168.68.122")

                // Open a connection to the URL
                val connection = url.openConnection() as HttpURLConnection

                // Set the request method to GET
                connection.requestMethod = "GET"

                // Read the response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                reader.forEachLine { response.append(it) }
                reader.close()

                // Parse the response as JSON
                val jsonResponse = JSONObject(response.toString())

                // Extract values from the JSON object
                val points = jsonResponse.getInt("points")
                val largeBottleCount = jsonResponse.getInt("largeBottleCount")
                val smallBottleCount = jsonResponse.getInt("smallBottleCount")

                // Update the UI on the main thread
                runOnUiThread {
                    smallBottlesValue.text = smallBottleCount.toString()
                    bigBottlesValue.text = largeBottleCount.toString()
                    totalPointsValue.text = points.toString()
                }
            } catch (e: Exception) {
                // Handle exceptions such as network issues
                Log.e("HomePage", "Error fetching data", e)
                runOnUiThread {
//                    Toast.makeText(this@HomePage, "Failed to fetch data", Toast.LENGTH_SHORT)
//                        .show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the handler when the activity is destroyed to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }
}
