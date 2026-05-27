package com.example.rescuelink

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

/**
 * Legacy MapsActivity — retained as a stub only.
 * All map functionality is now handled by Leaflet.js inside
 * the WebView rendered within DashboardScreen's MapScreen composable.
 * This activity is no longer registered in AndroidManifest.xml.
 */
class MapsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish() // Should never be reached; redirect to MainActivity if invoked accidentally
    }
}