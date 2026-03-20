package com.gow.smaitrobot.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.gow.smaitrobot.R

/**
 * Top logo bar showing SJSU, WiE, and BioRob Lab branding.
 *
 * Layout: SJSU (left) | WiE (center, dominant) | BioRob (right)
 * Transparent background — blends with WiE gradient behind it.
 * WiE logo fills the center. SJSU and BioRob are 1.7x their original sizes.
 */
@Composable
fun TopLogoBar(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SJSU ME logo (left) — 1.7x bigger
        Image(
            painter = painterResource(id = R.drawable.sjsu_logo),
            contentDescription = "SJSU Mechanical Engineering Logo",
            modifier = Modifier.height(170.dp),
            contentScale = ContentScale.Fit
        )

        // WiE logo (center) — prominent but not stretched
        Image(
            painter = painterResource(id = R.drawable.wie_logo),
            contentDescription = "Silicon Valley Women in Engineering Logo",
            modifier = Modifier
                .weight(1f)
                .height(100.dp)
                .padding(horizontal = 20.dp),
            contentScale = ContentScale.Fit
        )

        // BioRob Lab logo (right) — 1.7x bigger
        Image(
            painter = painterResource(id = R.drawable.biorob_logo),
            contentDescription = "BioRob Lab Logo",
            modifier = Modifier.height(150.dp),
            contentScale = ContentScale.Fit
        )
    }
}
