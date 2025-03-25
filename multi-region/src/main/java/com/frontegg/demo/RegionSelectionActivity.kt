package com.frontegg.demo

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.frontegg.android.FronteggApp


/**
 * Activity responsible for handling region selection in a multi-region authentication setup.
 * Allows users to choose between different regional authentication endpoints (EU or US).
 */
class RegionSelectionActivity : AppCompatActivity() {
    /**
     * Initializes the activity and sets up the region selection layout.
     * Called when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_region_selection)
    }

    /**
     * Sets up region selection button listeners when the activity becomes visible.
     * - EU button initializes Frontegg SDK with European region
     * - US button initializes Frontegg SDK with United States region
     * After region selection, the activity closes and returns to previous screen.
     */
    override fun onResume() {
        super.onResume()

        // Get references to region selection buttons
        val euButton = findViewById<LinearLayout>(R.id.euButton)
        val usButton = findViewById<LinearLayout>(R.id.usButton)

        // Configure EU region selection
        euButton.setOnClickListener {
            // Initialize Frontegg SDK with European region
            FronteggApp.getInstance().initWithRegion("eu")
            finish()
        }

        // Configure US region selection
        usButton.setOnClickListener {
            // Initialize Frontegg SDK with United States region
            FronteggApp.getInstance().initWithRegion("us")
            finish()
        }
    }
}