package com.c1ctech.barcodescannerexp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import com.google.firebase.FirebaseApp
import com.c1ctech.barcodescannerexp.databinding.ActivityMainBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var firestore: FirebaseFirestore
    private var isProcessingScan = false
    private lateinit var database: DatabaseReference

    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupCamera()
        FirebaseApp.initializeApp(this)
        firestore = FirebaseFirestore.getInstance()



        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.e("Firebase", "Firebase is not initialized!")
        } else {
            Log.d("Firebase", "Firebase is initialized successfully!")
        }

    }

    private fun setupCamera() {
        previewView = findViewById(R.id.preview_view)
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(CameraXViewModel::class.java)
            .processCameraProvider
            .observe(this) { provider: ProcessCameraProvider? ->
                cameraProvider = provider
                if (isCameraPermissionGranted()) {
                    bindCameraUseCases()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.CAMERA),
                        PERMISSION_CAMERA_REQUEST
                    )
                }
            }
    }

    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetRotation(previewView!!.display.rotation)
            .build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)

        try {

            cameraProvider!!.bindToLifecycle(
                this,
                cameraSelector!!,
                previewUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    private fun bindAnalyseUseCase() {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()

        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)

        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetRotation(previewView!!.display.rotation)
            .build()

        // Initialize our background executor
        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(
            cameraExecutor,
            ImageAnalysis.Analyzer { imageProxy ->
                processImageProxy(barcodeScanner, imageProxy)
            }
        )

        try {
            cameraProvider!!.bindToLifecycle(
                /* lifecycleOwner= */this,
                cameraSelector!!,
                analysisUseCase
            )
        } catch (illegalStateException: IllegalStateException) {
            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
        } catch (illegalArgumentException: IllegalArgumentException) {
            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
        }
    }

    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.forEach { barcode ->
                    val rawValue = barcode.rawValue
                    binding.tvScannedData.text = rawValue

                    if (rawValue != null) {
                        handleScannedQRCode(rawValue)
                    }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, it.message ?: it.toString())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                bindCameraUseCases()
            } else {
                Log.e(TAG, "no camera permission")
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName

        private const val PERMISSION_CAMERA_REQUEST = 1
    }

//private fun handleScannedQRCode(qrCodeValue: String) {
//    if (isProcessingScan) return
//
//    isProcessingScan = true
//
//    val collectionRef = firestore.collection("qrCodes")
//    collectionRef.whereEqualTo("id", qrCodeValue).get()
//        .addOnSuccessListener { querySnapshot ->
//            if (!querySnapshot.isEmpty) {
//                val document = querySnapshot.documents.first()
//                val email = document.getString("email") ?: "No email"
//                Log.d(TAG, "Document found: email = $email")
//
//                thread {
//                    var isEspCommunicationSuccessful = false // Flag to check ESP communication success
//
//                    try {
//                        val esp8266Url = "http://192.168.16.103/update" // Corrected IP address
//                        Log.d(TAG, "Connecting to ESP8266 at: $esp8266Url")
//                        val url = URL(esp8266Url)
//                        with(url.openConnection() as HttpURLConnection) {
//                            requestMethod = "POST"
//                            doOutput = true
//                            setRequestProperty("Content-Type", "application/json")
//
//                            // Send JSON data
//                            val postData = """{"email":"$email"}"""
//                            Log.d(TAG, "Sending data: $postData")
//
//                            outputStream.use {
//                                it.write(postData.toByteArray())
//                            }
//                            Log.d(TAG, "Output Stream written successfully")
//
//                            val responseCode = responseCode
//                            Log.d(TAG, "Response Code: $responseCode")
//                            if (responseCode == HttpURLConnection.HTTP_OK) {
//                                Log.d(TAG, "Successfully sent QR code to ESP8266")
//                                isEspCommunicationSuccessful = true // Mark communication as successful
//                            } else {
//                                Log.e(TAG, "Failed to send QR code to ESP8266, Response Code: $responseCode")
//                                Log.e(TAG, "Response Message: ${responseMessage}")
//                            }
//                        }
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Exception in sending QR code to ESP8266: ", e)
//                    } finally {
//                        if (isEspCommunicationSuccessful) {
//                            navigateToUserPage(email)
//                        } else {
//                            Log.e(TAG, "ESP communication failed.")
//                        }
//                        isProcessingScan = false
//                    }
//                }
//            } else {
//                Log.d(TAG, "No such document with ID: $qrCodeValue")
//                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
//                isProcessingScan = false
//            }
//        }
//        .addOnFailureListener { exception ->
//            Log.d(TAG, "Failed to get document: ", exception)
//            Toast.makeText(this, "Failed to retrieve user", Toast.LENGTH_SHORT).show()
//            isProcessingScan = false
//        }
//}
//
//    private fun navigateToUserPage(email: String) {
//        Log.d(TAG, "Proceed to intent")
//
//        val intent = Intent(this, UserPageActivity::class.java).apply {
//            putExtra("USER_EMAIL", email)
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
//        }
//        startActivity(intent)
//    }package com.c1ctech.barcodescannerexp
//
//import android.Manifest
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.util.Log
//import android.widget.Toast
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.app.ActivityCompat
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.ViewModelProvider
//import com.google.mlkit.vision.barcode.Barcode
//import com.google.mlkit.vision.barcode.BarcodeScanner
//import com.google.mlkit.vision.barcode.BarcodeScannerOptions
//import com.google.mlkit.vision.barcode.BarcodeScanning
//import com.google.mlkit.vision.common.InputImage
//import java.util.concurrent.Executors
//import com.google.firebase.FirebaseApp;
//import com.c1ctech.barcodescannerexp.databinding.ActivityMainBinding
//import com.google.firebase.firestore.FirebaseFirestore
//import org.json.JSONObject
//import java.net.HttpURLConnection
//import java.net.URL
//import kotlin.concurrent.thread
//
//class MainActivity : AppCompatActivity() {
//    private lateinit var binding: ActivityMainBinding
//    private lateinit var firestore: FirebaseFirestore
//    private var isProcessingScan = false
//
//    private var previewView: PreviewView? = null
//    private var cameraProvider: ProcessCameraProvider? = null
//    private var cameraSelector: CameraSelector? = null
//    private var lensFacing = CameraSelector.LENS_FACING_FRONT
//    private var previewUseCase: Preview? = null
//    private var analysisUseCase: ImageAnalysis? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        binding = ActivityMainBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//        setupCamera()
//        FirebaseApp.initializeApp(this)
//        firestore = FirebaseFirestore.getInstance()
//
//        if (FirebaseApp.getApps(this).isEmpty()) {
//            Log.e("Firebase", "Firebase is not initialized!")
//        } else {
//            Log.d("Firebase", "Firebase is initialized successfully!")
//        }
//
//    }
//
//    private fun setupCamera() {
//        previewView = findViewById(R.id.preview_view)
//        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
//        ViewModelProvider(
//            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
//        ).get(CameraXViewModel::class.java)
//            .processCameraProvider
//            .observe(this) { provider: ProcessCameraProvider? ->
//                cameraProvider = provider
//                if (isCameraPermissionGranted()) {
//                    bindCameraUseCases()
//                } else {
//                    ActivityCompat.requestPermissions(
//                        this,
//                        arrayOf(Manifest.permission.CAMERA),
//                        PERMISSION_CAMERA_REQUEST
//                    )
//                }
//            }
//    }
//
//    private fun bindCameraUseCases() {
//        bindPreviewUseCase()
//        bindAnalyseUseCase()
//    }
//
//    private fun bindPreviewUseCase() {
//        if (cameraProvider == null) {
//            return
//        }
//        if (previewUseCase != null) {
//            cameraProvider!!.unbind(previewUseCase)
//        }
//
//        previewUseCase = Preview.Builder()
//            .setTargetRotation(previewView!!.display.rotation)
//            .build()
//        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
//
//        try {
//
//            cameraProvider!!.bindToLifecycle(
//                this,
//                cameraSelector!!,
//                previewUseCase
//            )
//        } catch (illegalStateException: IllegalStateException) {
//            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
//        } catch (illegalArgumentException: IllegalArgumentException) {
//            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
//        }
//    }
//
//    private fun bindAnalyseUseCase() {
//        val options = BarcodeScannerOptions.Builder()
//            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
//
//        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(options)
//
//        if (cameraProvider == null) {
//            return
//        }
//        if (analysisUseCase != null) {
//            cameraProvider!!.unbind(analysisUseCase)
//        }
//
//        analysisUseCase = ImageAnalysis.Builder()
//            .setTargetRotation(previewView!!.display.rotation)
//            .build()
//
//        // Initialize our background executor
//        val cameraExecutor = Executors.newSingleThreadExecutor()
//
//        analysisUseCase?.setAnalyzer(
//            cameraExecutor,
//            ImageAnalysis.Analyzer { imageProxy ->
//                processImageProxy(barcodeScanner, imageProxy)
//            }
//        )
//
//        try {
//            cameraProvider!!.bindToLifecycle(
//                /* lifecycleOwner= */this,
//                cameraSelector!!,
//                analysisUseCase
//            )
//        } catch (illegalStateException: IllegalStateException) {
//            Log.e(TAG, illegalStateException.message ?: "IllegalStateException")
//        } catch (illegalArgumentException: IllegalArgumentException) {
//            Log.e(TAG, illegalArgumentException.message ?: "IllegalArgumentException")
//        }
//    }
//
//    private fun processImageProxy(
//        barcodeScanner: BarcodeScanner,
//        imageProxy: ImageProxy
//    ) {
//        val inputImage =
//            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)
//
//        barcodeScanner.process(inputImage)
//            .addOnSuccessListener { barcodes ->
//                barcodes.forEach { barcode ->
//                    val rawValue = barcode.rawValue
//                    binding.tvScannedData.text = rawValue
//
//                    if (rawValue != null) {
//                        handleScannedQRCode(rawValue)
//                    }
//                }
//            }
//            .addOnFailureListener {
//                Log.e(TAG, it.message ?: it.toString())
//            }
//            .addOnCompleteListener {
//                imageProxy.close()
//            }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray
//    ) {
//        if (requestCode == PERMISSION_CAMERA_REQUEST) {
//            if (isCameraPermissionGranted()) {
//                bindCameraUseCases()
//            } else {
//                Log.e(TAG, "no camera permission")
//            }
//        }
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//    }
//
//    private fun isCameraPermissionGranted(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            baseContext,
//            Manifest.permission.CAMERA
//        ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    companion object {
//
//        private val TAG = MainActivity::class.java.simpleName
//
//        private const val PERMISSION_CAMERA_REQUEST = 1
//    }
////    private fun handleScannedQRCode(qrCodeValue: String) {
////        if (isProcessingScan) return
////
////        isProcessingScan = true
////
////        // Send the scanned QR code to ESP8266
////        thread {
////            var isEspCommunicationSuccessful = false // Flag to check ESP communication success
////
////            try {
////                // Updated URL of your ESP8266 endpoint that processes QR codes
////                val esp8266Url = "http://192.168.16.103/update" // Corrected IP address
////                Log.d(TAG, "Connecting to ESP8266 at: $esp8266Url")
////                val url = URL(esp8266Url)
////                with(url.openConnection() as HttpURLConnection) {
////                    requestMethod = "POST"
////                    doOutput = true
////                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
////
////                    val postData = "qrCode=$qrCodeValue"
////                    Log.d(TAG, "Sending data: $postData")
////
////                    outputStream.use {
////                        it.write(postData.toByteArray())
////                    }
////                    Log.d(TAG, "Output Stream written successfully")
////
////                    val responseCode = responseCode
////                    Log.d(TAG, "Response Code: $responseCode")
////                    if (responseCode == HttpURLConnection.HTTP_OK) {
////                        Log.d(TAG, "Successfully sent QR code to ESP8266")
////                        isEspCommunicationSuccessful = true // Mark communication as successful
////                    } else {
////                        Log.e(TAG, "Failed to send QR code to ESP8266, Response Code: $responseCode")
////                        Log.e(TAG, "Response Message: ${responseMessage}")
////                    }
////                }
////            } catch (e: Exception) {
////                Log.e(TAG, "Exception in sending QR code to ESP8266: ", e)
////            } finally {
////                if (isEspCommunicationSuccessful) {
////                    // Proceed only if ESP communication was successful
////                    val collectionRef = firestore.collection("qrCodes")
////
////                    collectionRef.whereEqualTo("id", qrCodeValue).get()
////                        .addOnSuccessListener { querySnapshot ->
////                            if (!querySnapshot.isEmpty) {
////                                val document = querySnapshot.documents.first()
////                                val email = document.getString("email") ?: "No email"
////                                Log.d(TAG, "Document found: email = $email")
////                                navigateToUserPage(email)
////                            } else {
////                                Log.d(TAG, "No such document with ID: $qrCodeValue")
////                                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
////                            }
////                        }
////                        .addOnFailureListener { exception ->
////                            Log.d(TAG, "Failed to get document: ", exception)
////                            Toast.makeText(this, "Failed to retrieve user", Toast.LENGTH_SHORT).show()
////                        }
////                        .addOnCompleteListener {
////                            isProcessingScan = false
////                        }
////                } else {
////                    // ESP communication failed, reset the flag
////                    isProcessingScan = false
////                }
////            }
////        }
////    }
//
////    private fun handleScannedQRCode(qrCodeValue: String) {
////        if (isProcessingScan) return
////
////        isProcessingScan = true
////
////        val collectionRef = firestore.collection("qrCodes")
////        collectionRef.whereEqualTo("id", qrCodeValue).get()
////            .addOnSuccessListener { querySnapshot ->
////                if (!querySnapshot.isEmpty) {
////                    val document = querySnapshot.documents.first()
////                    val email = document.getString("email") ?: "No email"
////                    Log.d(TAG, "Document found: email = $email")
////
////                    thread {
////                        var isEspCommunicationSuccessful = false // Flag to check ESP communication success
////
////                        try {
////                            val esp8266Url = "http://192.168.16.103/update" // Corrected IP address
////                            Log.d(TAG, "Connecting to ESP8266 at: $esp8266Url")
////                            val url = URL(esp8266Url)
////                            with(url.openConnection() as HttpURLConnection) {
////                                requestMethod = "POST"
////                                doOutput = true
////                                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
////
////                                val postData = "qrCode=$qrCodeValue"
////                                Log.d(TAG, "Sending data: $postData")
////
////                                outputStream.use {
////                                    it.write(postData.toByteArray())
////                                }
////                                Log.d(TAG, "Output Stream written successfully")
////
////                                val responseCode = responseCode
////                                Log.d(TAG, "Response Code: $responseCode")
////                                if (responseCode == HttpURLConnection.HTTP_OK) {
////                                    Log.d(TAG, "Successfully sent QR code to ESP8266")
////                                    isEspCommunicationSuccessful = true // Mark communication as successful
////                                } else {
////                                    Log.e(TAG, "Failed to send QR code to ESP8266, Response Code: $responseCode")
////                                    Log.e(TAG, "Response Message: ${responseMessage}")
////                                }
////                            }
////                        } catch (e: Exception) {
////                            Log.e(TAG, "Exception in sending QR code to ESP8266: ", e)
////                        } finally {
////                            if (isEspCommunicationSuccessful) {
////                                navigateToUserPage(email)
////                            } else {
////                                Log.e(TAG, "ESP communication failed.")
////                            }
////                            isProcessingScan = false
////                        }
////                    }
////                } else {
////                    Log.d(TAG, "No such document with ID: $qrCodeValue")
////                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
////                    isProcessingScan = false
////                }
////            }
////            .addOnFailureListener { exception ->
////                Log.d(TAG, "Failed to get document: ", exception)
////                Toast.makeText(this, "Failed to retrieve user", Toast.LENGTH_SHORT).show()
////                isProcessingScan = false
////            }
////    }

    private fun handleScannedQRCode(qrCodeValue: String) {
        if (isProcessingScan) return

        isProcessingScan = true

        val collectionRef = firestore.collection("qrCodes")
        collectionRef.whereEqualTo("id", qrCodeValue).get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents.first()
                    val userId = document.id // Use the document ID as the user ID
                    Log.d(TAG, "Document found: userId = $userId")


                    // Now retrieve the user document using the userId
                    firestore.collection("users").document(userId).get()
                        .addOnSuccessListener { userDocument ->
                            if (userDocument.exists()) {
                                val firstName = userDocument.getString("firstname") // Assuming firstName field exists
                                val lastName = userDocument.getString("lastname")   // Assuming lastName field exists

                                Log.d(TAG, "User found: firstName = $firstName, lastName = $lastName")

                                val fullName = "$firstName" + " " + "$lastName"
                                // Navigate to the user page with additional user info if needed
                                navigateToUserPage(userId, fullName)

                            } else {
                                Log.d(TAG, "No user document found with ID: $userId")
                                Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                            }
                            isProcessingScan = false
                        }
                        .addOnFailureListener { exception ->
                            Log.d(TAG, "Failed to get user document: ", exception)
                            Toast.makeText(this, "Failed to retrieve user", Toast.LENGTH_SHORT).show()
                            isProcessingScan = false
                        }




                } else {
                    Log.d(TAG, "No such document with ID: $qrCodeValue")
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                    isProcessingScan = false
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "Failed to get document: ", exception)
                Toast.makeText(this, "Failed to retrieve user", Toast.LENGTH_SHORT).show()
                isProcessingScan = false
            }
    }


    private fun navigateToUserPage(userId: String, fullName:String) {
        Log.d(TAG, "Proceed to intent")
        database = FirebaseDatabase.getInstance().getReference("App/Touchscreen/Data")

        database.child("indicator").removeValue()

        // Retrieve the text values from the Intent
        val smallBottlesText = intent.getStringExtra("smallBottles")?.toIntOrNull() ?: 0
        val bigBottlesText = intent.getStringExtra("bigBottles")?.toIntOrNull() ?: 0
        val totalPointsText = intent.getStringExtra("totalPoints")?.toIntOrNull() ?: 0


        val intent = Intent(this, UserPageActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("FULL_NAME", fullName)

            putExtra("USER_POINTS", totalPointsText)
            putExtra("USER_LARGE_BOTTLE_COUNT", bigBottlesText)
            putExtra("USER_SMALL_BOTTLE_COUNT", smallBottlesText)
        }

        startActivity(intent)
        finish()
    }
}