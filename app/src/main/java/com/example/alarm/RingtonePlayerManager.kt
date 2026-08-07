package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class RingtonePlayerManager private constructor(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    init {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun startAlarmRingtone(ringtoneUriString: String?, isVibrate: Boolean) {
        stop()

        try {
            val uri = if (!ringtoneUriString.isNull_Blank() && ringtoneUriString != "default") {
                android.net.Uri.parse(ringtoneUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error playing alarm audio", e)
        }

        if (isVibrate && vibrator != null) {
            try {
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                Log.e("RingtonePlayer", "Error vibrating device", e)
            }
        }
    }

    fun stop() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error stopping player", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e("RingtonePlayer", "Error stopping vibration", e)
        }
    }

    companion object {
        @Volatile
        private var instance: RingtonePlayerManager? = null

        fun getInstance(context: Context): RingtonePlayerManager {
            return instance ?: synchronized(this) {
                instance ?: RingtonePlayerManager(context.applicationContext).also { instance = it }
            }
        }

        private fun String?.isNull_Blank(): Boolean = this.isNullOrEmpty() || this.trim().isEmpty()
    }
}
