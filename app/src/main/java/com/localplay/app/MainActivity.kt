package com.localplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.localplay.app.feature.permissions.PermissionScreen
import com.localplay.app.feature.permissions.rememberAudioPermissionState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalPlayApp()
        }
    }
}

@Composable
fun LocalPlayApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val permissionGranted = rememberAudioPermissionState()

            if (permissionGranted.value) {
                LibraryPlaceholder()
            } else {
                PermissionScreen(onPermissionGranted = { permissionGranted.value = true })
            }
        }
    }
}

/** Stand-in for the real library screen, which arrives in Phase 4. */
@Composable
private fun LibraryPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Permission granted — library scan comes in Phase 2")
    }
}
