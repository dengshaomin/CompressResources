package com.code.resourcecompress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import kotlin.math.log

class MainActivity : AppCompatActivity() {
    lateinit var bitmap: Bitmap
    lateinit var bitmap1: Bitmap
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bitmap = BitmapFactory.decodeResource(resources,R.mipmap.cars_0)
        bitmap1 = BitmapFactory.decodeResource(resources,R.mipmap.cars_3)
        image_0.setImageBitmap(bitmap)
        image_1.setImageBitmap(bitmap)
        image_2.setImageBitmap(bitmap1)

    }
}