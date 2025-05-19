package com.lottiefiles.example

import android.app.Application
import android.util.Log

class DotLottieApplication : Application() {
    companion object {
        private const val TAG = "DotLottieApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize logging
        Log.d(TAG, "Debug logging initialized")
    }
} 