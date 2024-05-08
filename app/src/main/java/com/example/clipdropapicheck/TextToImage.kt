package com.example.clipdropapicheck

import android.app.ProgressDialog
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class TextToImage : AppCompatActivity() {

    private lateinit var etMessage: EditText
    private lateinit var img: ImageView
    private lateinit var createButton: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_to_image)

        etMessage = findViewById(R.id.etMessage)
        img = findViewById(R.id.img)
        createButton = findViewById(R.id.create)

        createButton.setOnClickListener {
            val prompt = etMessage.text.toString()
            if (prompt.isNotEmpty()) {
                sendTextToImageRequest(prompt)
            }
        }
    }

    private fun sendTextToImageRequest(prompt: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Generating image...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .build()

        val request = Request.Builder()
            .header("x-api-key", resources.getString(R.string.api_key))
            .url("https://clipdrop-api.co/text-to-image/v1")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                progressDialog.dismiss()
                // Handle error (e.g., show an error message)
            }

            override fun onResponse(call: Call, response: Response) {
                progressDialog.dismiss()
                if (!response.isSuccessful) {
                    // Handle unsuccessful response (e.g., show an error message)
                    return
                }

                // Handle successful response
                val responseData = response.body?.bytes()

                // Update the ImageView with the image data
                runOnUiThread {
                    responseData?.let {
                        val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                        img.setImageBitmap(bitmap)
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