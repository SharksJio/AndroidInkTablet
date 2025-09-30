package com.sharks.androidinktablet

import android.app.Application

/**
 * Application class for AndroidInkTablet.
 * Initializes global app resources and configurations.
 */
class InkTabletApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize any global resources here
        // This could include ML Kit models, database, preferences, etc.
    }
}