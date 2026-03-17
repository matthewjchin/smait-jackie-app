package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gow.smaitrobot.R

/**
 * Top logo bar showing SJSU and BioRob Lab branding.
 *
 * Uses the actual logo images from drawable resources.
 * Height: 80dp for visibility on Jackie's kiosk display.
 */
@Composable
fun TopLogoBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SJSU ME logo (left)
        Image(
            painter = painterResource(id = R.drawable.sjsu_logo),
            contentDescription = "SJSU Mechanical Engineering Logo",
            modifier = Modifier.height(200.dp),
            contentScale = ContentScale.Fit
        )

        // BioRob Lab logo (right)
        Image(
            painter = painterResource(id = R.drawable.biorob_logo),
            contentDescription = "BioRob Lab Logo",
            modifier = Modifier.height(180.dp),
            contentScale = ContentScale.Fit
        )
    }
}
