package com.urbansidequest.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppSurfaceMuted
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

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
            .height(72.dp),
        color = AppSurface,
        border = BorderStroke(1.dp, AppBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            UrbanNavigationItem(
                text = "地图",
                icon = Icons.Filled.Map,
                selected = selectedDestination == UrbanDestination.Map,
                onClick = onMapClick
            )
            UrbanNavigationItem(
                text = "生成",
                icon = Icons.Filled.AddLocationAlt,
                selected = selectedDestination == UrbanDestination.Routes,
                onClick = onRoutesClick
            )
            UrbanNavigationItem(
                text = "我的",
                icon = Icons.Filled.Person,
                selected = selectedDestination == UrbanDestination.Profile,
                onClick = onProfileClick
            )
        }
    }
}

@Composable
private fun UrbanNavigationItem(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) DeepTeal else AppTextMuted
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .background(if (selected) AppSurfaceMuted else Color.Transparent, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = null,
            tint = contentColor
        )
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
