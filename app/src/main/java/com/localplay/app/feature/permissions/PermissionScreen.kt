package com.localplay.app.feature.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * The single runtime permission this app needs to read local audio files.
 * API 33+ uses the granular READ_MEDIA_AUDIO; older versions fall back to
 * READ_EXTERNAL_STORAGE (declared with maxSdkVersion="32" in the manifest).
 */
val audioPermission: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun rememberAudioPermissionState(): MutableState<Boolean> {
    val context = LocalContext.current
    return remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
}

@Composable
fun PermissionScreen(onPermissionGranted: () -> Unit) {
    var wasDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onPermissionGranted()
        } else {
            wasDenied = true
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "LocalPlay needs access to your music",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "This only reads audio files stored on your device. " +
                    "LocalPlay has no internet permission at all, so nothing " +
                    "ever leaves your phone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { launcher.launch(audioPermission) }) {
                Text("Grant access")
            }

            if (wasDenied) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Without this permission LocalPlay can't find any " +
                        "music. You can grant it later from Android Settings " +
                        "> Apps > LocalPlay > Permissions.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
