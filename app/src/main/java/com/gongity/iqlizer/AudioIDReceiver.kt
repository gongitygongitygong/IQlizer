package com.gongity.iqlizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log

class AudioIDReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Grab the ID from the music app that just started
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)

        if (sessionId != AudioEffect.ERROR_BAD_VALUE && sessionId != 0) {
            Log.d("IQlizer", "Successfully caught session: $sessionId")

            // Send this ID to your AudioService
            val serviceIntent = Intent(context, AudioService::class.java).apply {
                putExtra("AUDIO_SESSION_ID", sessionId)
            }

            // Start the service to update the EQ session
            try {
                context.startService(serviceIntent)
            } catch (e: Exception) {
                // For Android 14+, if the app is in background, you might need
                // to handle foreground service start restrictions here.
                Log.e("IQlizer", "Service start failed: ${e.message}")
            }
        }
    }
}