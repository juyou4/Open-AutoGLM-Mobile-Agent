package com.example.autoglmagent.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val accessibilityEnabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        if (!accessibilityEnabled) return false

        val expected = ComponentName(context, AgentAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (service in splitter) {
            val enabled = ComponentName.unflattenFromString(service)
            if (enabled == expected) return true
        }
        return false
    }
}
