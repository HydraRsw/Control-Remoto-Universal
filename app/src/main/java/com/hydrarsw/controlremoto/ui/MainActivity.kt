package com.hydrarsw.controlremoto.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.hydrarsw.controlremoto.update.AppVisibility
import com.hydrarsw.controlremoto.update.UpdateWorker

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ControlRemotoApp(viewModel) }
        if (savedInstanceState == null) {
            viewModel.checkForUpdate()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        UpdateWorker.schedule(this)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.inForeground = true
        viewModel.onForeground()
    }

    override fun onStop() {
        AppVisibility.inForeground = false
        viewModel.onBackground()
        super.onStop()
    }
}
