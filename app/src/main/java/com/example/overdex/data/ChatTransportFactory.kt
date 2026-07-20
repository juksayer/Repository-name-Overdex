package com.example.overdex.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Factory for creating the appropriate [ChatTransport] based on system availability.
 */
object ChatTransportFactory {
    
    /**
     * Creates a transport layer, preferring Firebase and falling back to a Mock
     * implementation if Firebase initialization fails.
     */
    fun create(
        context: Context,
        myTrainerId: String,
        partnerTrainerId: String
    ): ChatTransport {
        return try {
            // Attempt to initialize Firebase if not already done
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.i("CHAT_TRANSPORT", "Firebase initialized successfully.")
            }
            
            val transport = FirebaseChatTransport(myTrainerId, partnerTrainerId)
            Log.i("CHAT_TRANSPORT", "CHAT TRANSPORT: FirebaseChatTransport")
            transport
        } catch (e: Exception) {
            Log.w("CHAT_TRANSPORT", "CHAT TRANSPORT: MockChatTransport")
            
            // Determine reasoning
            val reason = if (e is IllegalStateException && e.message?.contains("google-services.json") == true) {
                "google-services.json not detected"
            } else {
                "Firebase initialization failed: ${e.message}"
            }
            Log.w("CHAT_TRANSPORT", "Reason: $reason")

            MockChatTransport()
        }
    }
}
