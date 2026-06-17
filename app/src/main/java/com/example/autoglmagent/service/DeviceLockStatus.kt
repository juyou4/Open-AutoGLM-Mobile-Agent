package com.example.autoglmagent.service

import android.app.KeyguardManager
import android.content.Context

object DeviceLockStatus {
    fun isLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return keyguardManager?.isKeyguardLocked == true
    }
}
