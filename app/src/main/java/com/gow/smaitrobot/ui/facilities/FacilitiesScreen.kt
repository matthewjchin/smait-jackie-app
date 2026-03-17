package com.gow.smaitrobot.ui.facilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gow.smaitrobot.data.model.PoiItem
import com.gow.smaitrobot.ui.common.SubScreenTopBar

/**
 * Facilities screen — searchable list of points of interest with navigation action.
 */
@Composable
fun FacilitiesScreen(
    viewModel: FacilitiesViewModel,
    navController: NavHostController
) {
    val filteredPois by viewModel.filteredPois.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        SubScreenTopBar(
            title = "Facilities",
            onBack = { navController.popBackStack() }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text(text = "Search facilities...", fontSize = 18.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp)
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }

                filteredPois.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching facilities",
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredPois, key = { it.name }) { poi ->
                            PoiCard(poi = poi, onNavigate = { viewModel.navigateTo(poi.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoiCard(poi: PoiItem, onNavigate: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = poi.humanName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (poi.category.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = poi.category.replaceFirstChar { it.uppercaseChar() },
                                fontSize = 14.sp
                            )
                        }
                    )
                }
                if (poi.floor.isNotBlank()) {
                    Text(
                        text = "Floor ${poi.floor}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNavigate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsWalk,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Take me there", fontSize = 18.sp)
            }
        }
    }
}
