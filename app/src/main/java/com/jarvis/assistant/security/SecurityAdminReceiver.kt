package com.jarvis.assistant.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class SecurityAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        val devicePolicyManager = getManager(context)
        val failedAttempts = devicePolicyManager.currentFailedPasswordAttempts
        
        Log.e("AishaSecurity", "ALERT: Kisi anjaan ne galat password dala! Total galti: $failedAttempts")

        try {
            when {
                failedAttempts == 2 -> {
                    // The hidden/invisible "stealth selfie" camera trigger has been removed
                    // (Round 1 security fix) — it opened the camera with no visible UI and no
                    // user consent. We still log the failed attempt. A real intruder-selfie
                    // feature, if wanted later, needs a visible camera screen with its own
                    // permission prompt — not an automatic background capture.
                    Log.i("AishaSecurity", "Second failed unlock attempt logged (camera capture disabled).")
                    saveIntruderLog(context, "Attempt 2: Logged (camera capture disabled)")
                }
                failedAttempts >= 3 -> {
                    // 3 Galti Par: Hardcore Anti-Theft Alarm & Vibration
                    Log.w("AishaSecurity", "MAX ATTEMPTS REACHED! Triggering Alarm...")
                    triggerHardcoreSiren(context)
                    saveIntruderLog(context, "Attempt 3: Siren Triggered")
                }
            }
        } catch (e: Exception) {
            Log.e("AishaSecurity", "Error in SecurityAdminReceiver: ${e.message}")
        }
    }

    private fun triggerHardcoreSiren(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // DND (Do Not Disturb) bypass karne ka try
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager.isVolumeFixed) {
                Log.w("AishaSecurity", "Volume is fixed, cannot override.")
            } else {
                // Volume ko zabardasti 100% max par set karna
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, AudioManager.FLAG_PLAY_SOUND)
            }

            // Siren Play karna (Default Alarm tone)
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                // Agar alarm tone na mile toh ringtone baja do
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
            
            val ringtone = RingtoneManager.getRingtone(context, alarmUri)
            ringtone?.play()

            // Sath mein phone ko vibrate bhi karna
            triggerVibration(context)

        } catch (e: Exception) {
            Log.e("AishaSecurity", "Failed to trigger siren: ${e.message}")
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (e: Exception) {
            Log.e("AishaSecurity", "Vibration failed: ${e.message}")
        }
    }

    private fun saveIntruderLog(context: Context, logMessage: String) {
        // Offline memory mein save karna taaki app khulne par Malik ko bataya ja sake
        val sharedPrefs = context.getSharedPreferences("AishaSecurityPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("INTRUDER_DETECTED", true)
            putString("LAST_INTRUDER_TIME", System.currentTimeMillis().toString())
            putString("INTRUDER_ACTION", logMessage)
            apply()
        }
    }
}
