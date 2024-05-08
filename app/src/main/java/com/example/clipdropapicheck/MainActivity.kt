package com.example.clipdropapicheck

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {


    private lateinit var createButton: Button
    private lateinit var removeButton: Button
    private lateinit var replaceButton: Button
    private lateinit var inpaintingButton: Button



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        createButton = findViewById(R.id.create)
        removeButton = findViewById(R.id.remove)
        replaceButton = findViewById(R.id.replace)
        inpaintingButton = findViewById(R.id.inpainting)

        createButton.setOnClickListener {
           startActivity(Intent(this@MainActivity,TextToImage::class.java))
        }
        removeButton.setOnClickListener {
            startActivity(Intent(this@MainActivity,BackgroundRemover::class.java))
        }
        replaceButton.setOnClickListener {
            startActivity(Intent(this@MainActivity,ReplaceBackground::class.java))
        }
        inpaintingButton.setOnClickListener {
            startActivity(Intent(this@MainActivity,InPainting::class.java))
        }
    }


}