package com.example.autoglmagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.example.autoglmagent.agent.AgentOrchestrator
import com.example.autoglmagent.data.SettingsRepository
import com.example.autoglmagent.ui.AgentApp

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val settingsRepository = SettingsRepository(applicationContext)
        val orchestrator = AgentOrchestrator.getInstance(applicationContext)

        setContent {
            AgentApp(
                settingsRepository = settingsRepository,
                orchestrator = orchestrator,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        AgentOrchestrator.getInstance(applicationContext).refreshServiceStatus()
    }
}
