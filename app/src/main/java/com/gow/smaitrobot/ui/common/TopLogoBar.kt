package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.gow.smaitrobot.R

/**
 * Top banner bar — event banner centered at ~2/3 screen width.
 */
@Composable
fun TopLogoBar(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.hfes_banner),
            contentDescription = "HFES Western Regional Meeting",
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .wrapContentHeight(),
            contentScale = ContentScale.FillWidth
        )
    }
}
