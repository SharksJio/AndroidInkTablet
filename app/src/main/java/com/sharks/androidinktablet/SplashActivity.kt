package com.sharks.androidinktablet

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * Splash screen activity with modern Android 12+ splash screen API
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    
    private companion object {
        const val SPLASH_DELAY = 1500L // 1.5 seconds
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before calling super
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_splash)
        
        // Navigate to main activity after delay
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, SPLASH_DELAY)
    }
}