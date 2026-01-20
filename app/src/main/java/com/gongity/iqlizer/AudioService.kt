package com.gongity.iqlizer

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class AudioService : Service() {

    private val binder = LocalBinder()
    private var equalizer: Equalizer? = null
    private var heartbeat: Visualizer? = null

    companion object {
        var eqBands = FloatArray(5) { 50f }
        var isEqEnabled = true
        var lastSessionId = 0
    }

    inner class LocalBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        attachEqualizer(lastSessionId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        val sessionId = intent?.getIntExtra("AUDIO_SESSION_ID", lastSessionId) ?: lastSessionId

        if (sessionId != lastSessionId || equalizer == null) {
            lastSessionId = sessionId
            attachEqualizer(sessionId)
        } else {
            equalizer?.enabled = true
            applyAllCurrentBands()
        }

        return START_STICKY
    }

    private fun attachEqualizer(sessionId: Int) {
        try {
            releaseEqualizer()
            equalizer = Equalizer(1000, sessionId)
            equalizer?.enabled = isEqEnabled

            if (heartbeat == null) {
                heartbeat = Visualizer(0)
                heartbeat?.enabled = true
            }

            applyAllCurrentBands()
            Log.d("IQlizer", "EQ Hooked to Session: $sessionId")
        } catch (e: Exception) {
            Log.e("IQlizer", "EQ Hook Error: ${e.message}")
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        isEqEnabled = enabled
        equalizer?.enabled = enabled
        if (!enabled) {
            // Reset to neutral when disabled
            for (i in eqBands.indices) {
                updateBandLevel(i, 50, applyToUI = false)
            }
        } else {
            // Re-apply when enabled
            applyAllCurrentBands()
        }
    }

    fun updateBandLevel(band: Int, progress: Int, applyToUI: Boolean = true) {
        if (applyToUI) {
            eqBands[band] = progress.toFloat()
        }

        if (!isEqEnabled && applyToUI) return

        val eq = equalizer ?: return
        try {
            val range = eq.bandLevelRange
            val level = (range[0] + (range[1] - range[0]) * (progress / 100f)).toInt()
            eq.setBandLevel(band.toShort(), level.toShort())
        } catch (e: Exception) {
            Log.e("IQlizer", "Band $band failed")
        }
    }

    fun applyAllCurrentBands() {
        for (i in eqBands.indices) {
            updateBandLevel(i, eqBands[i].toInt())
        }
    }

    private fun startAsForeground() {
        val channelId = "EQ_SERVICE"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Audio Engine", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Engine Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notification)
        }
    }

    private fun releaseEqualizer() {
        equalizer?.release()
        equalizer = null
        heartbeat?.release()
        heartbeat = null
        Log.d("IQlizer", "EQ Released")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        releaseEqualizer()
        stopSelf()
    }

    override fun onDestroy() {
        releaseEqualizer()
        super.onDestroy()
    }
}