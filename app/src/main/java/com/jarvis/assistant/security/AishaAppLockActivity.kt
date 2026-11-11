package com.jarvis.assistant.security

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.jarvis.assistant.ui.home.MainActivity
import java.util.concurrent.Executor

class AishaAppLockActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.BLACK)

        executor = ContextCompat.getMainExecutor(this)
        setupBiometricAuth()
        checkBiometricSupportAndAuthenticate()
    }

    private fun setupBiometricAuth() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "System Locked: $errString", Toast.LENGTH_SHORT).show()
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    
                    // Malik verify ho gaya, ab check karo kisi ne phone chhua tha kya
                    checkAndShowIntruderAlert()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Galat Fingerprint! Access Denied.", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Aisha Ultimate Security")
            .setSubtitle("Malik (Sanjeev Sir) ki pehchan zaroori hai")
            .setDescription("App access karne ke liye apna Fingerprint ya Face lagayein")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    private fun checkBiometricSupportAndAuthenticate() {
        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            // Agar phone mein password nahi laga hai
            checkAndShowIntruderAlert()
        }
    }

    private fun checkAndShowIntruderAlert() {
        val sharedPrefs = getSharedPreferences("AishaSecurityPrefs", Context.MODE_PRIVATE)
        val intruderDetected = sharedPrefs.getBoolean("INTRUDER_DETECTED", false)

        if (intruderDetected) {
            val lastTime = sharedPrefs.getString("LAST_INTRUDER_TIME", "Unknown Time")
            
            // Intruder ki details ka Popup Alert
            AlertDialog.Builder(this)
                .setTitle("🚨 SECURITY BREACH ALERT 🚨")
                .setMessage("Sanjeev Sir, aapke pichhe kisi anjaan vyakti ne aapka phone kholne ki koshish ki thi.\n\nTime: $lastTime")
                .setCancelable(false)
                .setPositiveButton("OK, Proceed to App") { _, _ ->
                    // Log clear karo aur app kholo
                    sharedPrefs.edit().clear().apply()
                    openMainActivity()
                }
                .show()
        } else {
            // Agar koi chor nahi aaya tha, toh sidha app khol do
            Toast.makeText(this, "Welcome back, Sanjeev Sir!", Toast.LENGTH_SHORT).show()
            openMainActivity()
        }
    }

    private fun openMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
