package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class GoMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initFirebase()
        com.example.data.crypto.CryptoManager.init(this)
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.fromResource(this)
                if (options != null) {
                    FirebaseApp.initializeApp(this, options)
                    Log.d("GoMemoApp", "Firebase initialized from resources for project: ${options.projectId}")
                } else {
                    FirebaseApp.initializeApp(this)
                    Log.d("GoMemoApp", "Firebase initialized via default provider")
                }
            } else {
                Log.d("GoMemoApp", "Firebase already initialized by provider")
            }
        } catch (e: Exception) {
            Log.e("GoMemoApp", "Error initializing Firebase: ${e.message}", e)
        }
    }
}

