package com.example.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.storage.FirebaseStorage

object FirebaseManager {
    val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
        }
    }

    val storage: FirebaseStorage by lazy {
        try {
            val app = FirebaseApp.getInstance()
            val bucket = app.options.storageBucket
            if (!bucket.isNullOrBlank()) {
                FirebaseStorage.getInstance("gs://$bucket")
            } else {
                FirebaseStorage.getInstance()
            }
        } catch (e: Exception) {
            FirebaseStorage.getInstance()
        }
    }
}
