package com.example.clipdropapicheck

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ReplaceBackground : AppCompatActivity() {
    private lateinit var orignalImg: ImageView
    private lateinit var pickImgButton: Button
    private lateinit var replaceBgButton: Button
    private lateinit var bgReplacedImg: ImageView

    private lateinit var client: OkHttpClient
    private lateinit var selectedImageFile: File
    private lateinit var progressDialog: ProgressDialog
    private lateinit var etMessage: EditText

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
        setContentView(R.layout.activity_replace_background)

        orignalImg = findViewById(R.id.orignalImg)
        pickImgButton = findViewById(R.id.pickImg)
        replaceBgButton = findViewById(R.id.replaceBg)
        bgReplacedImg = findViewById(R.id.bgReplacedImg)
        etMessage = findViewById(R.id.etMessage)

        client = OkHttpClient()
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Replacing background of image...")
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

        replaceBgButton.setOnClickListener {

            val prompt = etMessage.text.toString()
            if (prompt.isNotEmpty()) {
                replaceBackground(prompt)
            }
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


    private fun replaceBackground(prompt: String) {
        if (!selectedImageFile.exists()) {
            Log.d("clipdrop", "Selected image file does not exist.")
            return
        }
        Log.d("clipdrop", "Selected image file name : ${selectedImageFile.name}")
        progressDialog.show()
        Thread {
            val resizedBitmap = resizeImage(selectedImageFile)
            val resizedImageFile = createTempFile("resized_image", ".jpg")
            resizedImageFile.writeBitmap(resizedBitmap)

            val mediaType = "image/jpeg".toMediaType()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    resizedImageFile.name,
                    resizedImageFile.asRequestBody(mediaType)
                )
                .addFormDataPart("prompt", prompt)
                .build()

            val request = Request.Builder()
                .header("x-api-key", getString(R.string.api_key))
                .url("https://clipdrop-api.co/replace-background/v1")
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
                        val errorBody = response.body?.string()
                        Log.d("clipdrop", "Error body: $errorBody")
                        // Handle unsuccessful response and show a user-friendly message if needed
                        return
                    }

                    val responseData = response.body?.bytes()
                    Log.d("clipdrop", "responseData: $responseData")
                    runOnUiThread {
                        responseData?.let {
                            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                            bgReplacedImg.setImageBitmap(bitmap)
                        }
                    }
                }
            })

        }.start()



    }

    private fun File.writeBitmap(bitmap: Bitmap) {
        val stream = FileOutputStream(this)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        stream.close()
    }
    private fun resizeImage(inputFile: File): Bitmap {
        val maxImageHeight = 2048 // Maximum allowed height

        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(inputFile.absolutePath, options)

        val imageHeight = options.outHeight
        val scaleFactor = maxImageHeight.toFloat() / imageHeight.toFloat()

        val scaledBitmap = BitmapFactory.decodeFile(inputFile.absolutePath)
        val resizedBitmap = Bitmap.createScaledBitmap(
            scaledBitmap,
            (scaledBitmap.width * scaleFactor).toInt(),
            maxImageHeight,
            false
        )

        scaledBitmap.recycle()

        return resizedBitmap
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}