package com.urbansidequest.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTealDark

enum class UrbanDestination {
    Map,
    Routes,
    Profile
}

@Composable
fun UrbanBottomNavigationBar(
    selectedDestination: UrbanDestination,
    onMapClick: () -> Unit,
    onRoutesClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavigationItem(
                text = "地图",
                icon = Icons.Filled.Map,
                selected = selectedDestination == UrbanDestination.Map,
                onClick = onMapClick
            )
            BottomNavigationItem(
                text = "路线",
                icon = Icons.Filled.Directions,
                selected = selectedDestination == UrbanDestination.Routes,
                onClick = onRoutesClick
            )
            BottomNavigationItem(
                text = "我的",
                icon = Icons.Filled.Person,
                selected = selectedDestination == UrbanDestination.Profile,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
private fun BottomNavigationItem(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) Color.White else AppTextMuted
    val backgroundColor = if (selected) DeepTealDark else Color.Transparent

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = Color.Transparent,
                shape = CircleShape
            )
            .background(backgroundColor, CircleShape)
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = text,
            tint = contentColor
        )
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
