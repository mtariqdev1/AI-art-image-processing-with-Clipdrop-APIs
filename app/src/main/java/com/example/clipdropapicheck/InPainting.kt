package com.example.clipdropapicheck

import android.Manifest
import android.app.ProgressDialog
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.clipdropapicheck.utils.MaskView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class InPainting : AppCompatActivity() {
    private lateinit var orignalImg: ImageView
    private lateinit var responseImg: ImageView
    private lateinit var maskView: MaskView
    private lateinit var pickImgButton: Button
    private lateinit var maskImgButton: Button

    private lateinit var client: OkHttpClient
    private lateinit var selectedImageFile: File
    private lateinit var maskedImageFile: File
    private lateinit var progressDialog: ProgressDialog
    private lateinit var maskedBitmap: Bitmap

    private lateinit var undoButton: Button
    private lateinit var redoButton: Button


    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        Log.d("chkuris", "pickImage: $uri")
        if (uri != null) {
            Log.d("clipdrop", "uri not null")
            selectedImageFile = getFileFromUri(uri)!!
            orignalImg.setImageURI(uri)
        }
    }
    private lateinit var permissionLauncher: ActivityResultLauncher<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_painting)

        orignalImg = findViewById(R.id.orignalImg)
        responseImg = findViewById(R.id.responseImg)
        pickImgButton = findViewById(R.id.pickImg)
        maskView = findViewById(R.id.maskView)
        maskImgButton = findViewById(R.id.maskImg)
        undoButton = findViewById(R.id.undo)
        redoButton = findViewById(R.id.redo)

        client = OkHttpClient()
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Generating masked image...")
        progressDialog.setCancelable(false)

        // Initialize the permissionLauncher
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                pickImage.launch("image/*")
            } else {
                // Permission denied, handle accordingly
            }
        }

        pickImgButton.setOnClickListener {
            checkPermissionsAndPickImage()
        }

        maskImgButton.setOnClickListener {
            applyMask1()
        }


        undoButton.setOnClickListener {
            maskView.undo()
        }

        redoButton.setOnClickListener {
            maskView.redo()
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

    private fun getFileFromUri2(uri: Uri): File? {
        val contentResolver = applicationContext.contentResolver

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val filePath = it.getString(columnIndex)
                if (filePath != null) {
                    val sourceFile = File(filePath)
                    // Now you can copy the sourceFile to your desired location
                    return sourceFile
                }
            }
        }

        return null
    }

    private fun applyMask1() {
        if (::selectedImageFile.isInitialized) {
            val originalBitmap = BitmapFactory.decodeFile(selectedImageFile.absolutePath)
            val maskBitmap = maskView.maskBitmap

            val maskedBitmap = applyMask(originalBitmap, maskBitmap)


//            orignalImg.setImageBitmap(maskedBitmap)

            saveMaskedBitmapToGallery(maskedBitmap)
        }
    }

//    private fun applyMask(originalBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
//        val resultBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(resultBitmap)
//
//        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
//        val maskRect = Rect(0, 0, maskBitmap.width, maskBitmap.height)
//
//        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
//        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
//        canvas.drawBitmap(maskBitmap, maskRect, Rect(0, 0, originalBitmap.width, originalBitmap.height), paint)
//        paint.xfermode = null
//
//        return resultBitmap
//    }

    private fun applyMask(originalBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
        val resultBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw the original image
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        // Draw the mask at the calculated position
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        return resultBitmap
    }


    private fun saveMaskedBitmapToGallery(bitmap: Bitmap) {
        val displayName = "masked_image_${System.currentTimeMillis()}.jpg"
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }

        val contentResolver = applicationContext.contentResolver
        val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageDetails)
        Log.d("chkuris", "saveMaskedBitmapToGallery: $imageUri")
        maskedImageFile = imageUri?.let { getFileFromUri2(it) }!!
        try {

            val outputStream = imageUri?.let {
                contentResolver.openOutputStream(it)
            }
            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
            removeMaskedArea()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    private fun removeMaskedArea() {
        if (!selectedImageFile.exists() && !maskedImageFile.exists()) {
            Log.d("clipdrop", "Selected image file does not exist.")
            return
        }
        Log.d("clipdrop", "Selected image file name : ${selectedImageFile.name} , masked image file name : ${maskedImageFile.name}")
        progressDialog.show()
        Thread {
            val resizedBitmap = resizeImage(selectedImageFile)
            val resizedImageFile = createTempFile("resized_image", ".jpg")
            resizedImageFile.writeBitmap(resizedBitmap)

            val resizedBitmap2 = resizeImage(maskedImageFile)
            val resizedImageFile2 = createTempFile("resized_masked_image", ".jpg")
            resizedImageFile2.writeBitmap(resizedBitmap2)

            val mediaType = "image/jpeg".toMediaType()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    resizedImageFile.name,
                    resizedImageFile.asRequestBody(mediaType)
                )
                .addFormDataPart(
                    "mask_file",
                    resizedImageFile2.name,
                    resizedImageFile2.asRequestBody(mediaType)
                )
                .build()

            val request = Request.Builder()
                .header("x-api-key", getString(R.string.api_key))
                .url("https://clipdrop-api.co/cleanup/v1")
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
                            orignalImg.visibility= View.GONE
                            responseImg.setImageBitmap(bitmap)
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