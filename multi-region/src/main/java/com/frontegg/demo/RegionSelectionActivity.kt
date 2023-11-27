package com.frontegg.demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.LinearLayout
import com.frontegg.android.FronteggApp

class RegionSelectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)
    }


    override fun onResume() {
        super.onResume()

        val euButton = findViewById<LinearLayout>(R.id.euButton)
        val usButton = findViewById<LinearLayout>(R.id.usButton)

        euButton.setOnClickListener {
            FronteggApp.getInstance().initWithRegion("eu")
            finish()
        }

        usButton.setOnClickListener {
            FronteggApp.getInstance().initWithRegion("us")
            finish()
        }
    }
}