package com.gow.smaitrobot.ui.web

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.gow.smaitrobot.ui.common.SubScreenTopBar

/**
 * In-app WebView screen so users don't leave the app.
 * Shows a SubScreenTopBar with back button to return to Home.
 */
@Composable
fun WebViewScreen(
    url: String,
    navController: NavHostController
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(
            title = "Event Website",
            onBack = { navController.popBackStack() }
        )

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}
