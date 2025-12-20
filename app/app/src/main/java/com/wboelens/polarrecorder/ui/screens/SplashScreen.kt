package com.wboelens.polarrecorder.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun SplashScreen(
    onVideoComplete: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(color=0xFFF7F4E4)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Load video from raw resources
                    val videoUri = Uri.parse("android.resource://${context.packageName}/${com.wboelens.polarrecorder.R.raw.transition}")
                    setVideoURI(videoUri)

                    // Set listener for when video completes
                    setOnCompletionListener {
                        onVideoComplete()
                    }

                    // Set listener for any errors
                    setOnErrorListener { _, _, _ ->
                        // If video fails to load, skip to main app
                        onVideoComplete()
                        true
                    }

                    // Start playing
                    start()
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            // Cleanup if needed
        }
    }
}

