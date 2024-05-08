package com.example.clipdropapicheck

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class BackgroundRemover : AppCompatActivity() {
    private lateinit var orignalImg: ImageView
    private lateinit var pickImgButton: Button
    private lateinit var removeBgButton: Button
    private lateinit var bgRemovedImg: ImageView

    private lateinit var client: OkHttpClient
    private lateinit var selectedImageFile: File
    private lateinit var progressDialog: ProgressDialog

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        Log.d("clipdrop", "uri : $uri")
        if (uri != null) {
            Log.d("clipdrop", "uri not null")
            selectedImageFile = getFileFromUri(uri)!!
            orignalImg.setImageURI(uri)
        }
    }

    private lateinit var permissionLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_remover)

        orignalImg = findViewById(R.id.orignalImg)
        pickImgButton = findViewById(R.id.pickImg)
        removeBgButton = findViewById(R.id.removeBg)
        bgRemovedImg = findViewById(R.id.bgRemovedImg)

        client = OkHttpClient()
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Generating image...")
        progressDialog.setCancelable(false)

        // Initialize the permissionLauncher
        permissionLauncher = registerForActivityResult(RequestPermission()) { isGranted ->
            if (isGranted) {
                pickImage.launch("image/*")
            } else {
                // Permission denied, handle accordingly
            }
        }

        pickImgButton.setOnClickListener {
            checkPermissionsAndPickImage()
        }

        removeBgButton.setOnClickListener {
            removeBackground()
        }
    }

    private fun checkPermissionsAndPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Handle permissions using the Permission API (Android 12+)
            checkPermissionWithPermissionApi()
        } else {
            // Handle permissions using the legacy method (pre-Android 12)
            checkPermissionLegacy()
        }
    }

    private fun checkPermissionWithPermissionApi() {
        val permission = Manifest.permission.READ_MEDIA_IMAGES

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*")
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun checkPermissionLegacy() {
        val permission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*")
        } else {
            permissionLauncher.launch(permission)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickImage.launch("image/*")
        }
    }

    private fun getFileFromUri(uri: Uri): File? {
        val contentResolver = applicationContext.contentResolver

        // Generate a unique file name
        val fileName = "picked_image_${System.currentTimeMillis()}.jpg"

        val outputFile = File(filesDir, fileName)

        try {
            val inputStream = contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(outputFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return outputFile
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }


    private fun removeBackground() {
        if (!selectedImageFile.exists()) {
            Log.d("clipdrop", "Selected image file does not exist.")
            return
        }
        Log.d("clipdrop", "Selected image file name : ${selectedImageFile.name}")
        progressDialog.show()

        val mediaType = "image/jpeg".toMediaType()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image_file",
                selectedImageFile.name,
                selectedImageFile.asRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .header("x-api-key", getString(R.string.api_key))
            .url("https://clipdrop-api.co/remove-background/v1")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                progressDialog.dismiss()
                Log.d("clipdrop", "onFailure: ${e.message}")
                // Handle error and show a user-friendly message if needed
            }

            override fun onResponse(call: Call, response: Response) {
                progressDialog.dismiss()
                if (!response.isSuccessful) {
                    Log.d("clipdrop", "!isSuccessful: ${response.code}")
                    // Handle unsuccessful response and show a user-friendly message if needed
                    return
                }

                val responseData = response.body?.bytes()
                Log.d("clipdrop", "responseData: $responseData")
                runOnUiThread {
                    responseData?.let {
                        val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                        bgRemovedImg.setImageBitmap(bitmap)
                    }
                }
            }
        })
    }


    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}