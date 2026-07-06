package com.urbansidequest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.R
import com.urbansidequest.app.ui.theme.ActionBlue
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppTextMuted

enum class UrbanDestination {
    Discover,
    Map,
    Routes,
    Profile
}

@Composable
fun UrbanBottomNavigationBar(
    selectedDestination: UrbanDestination,
    onDiscoverClick: () -> Unit = {},
    onMapClick: () -> Unit,
    onRoutesClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        color = AppSurface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppBorder)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UrbanNavigationItem(
                        text = "发现",
                        selected = selectedDestination == UrbanDestination.Discover,
                        unselectedIconRes = R.drawable.nav_discover_unselected,
                        selectedIconRes = R.drawable.nav_discover_selected,
                        onClick = onDiscoverClick,
                        modifier = Modifier.weight(1f)
                    )
                    UrbanNavigationItem(
                        text = "地图",
                        selected = selectedDestination == UrbanDestination.Map,
                        unselectedIconRes = R.drawable.nav_map_unselected,
                        selectedIconRes = R.drawable.nav_map_selected,
                        onClick = onMapClick,
                        modifier = Modifier.weight(1f)
                    )
                    UrbanNavigationItem(
                        text = "路线",
                        selected = selectedDestination == UrbanDestination.Routes,
                        unselectedIconRes = R.drawable.nav_routes_unselected,
                        selectedIconRes = R.drawable.nav_routes_selected,
                        onClick = onRoutesClick,
                        modifier = Modifier.weight(1f)
                    )
                    UrbanNavigationItem(
                        text = "我的",
                        selected = selectedDestination == UrbanDestination.Profile,
                        unselectedIconRes = R.drawable.nav_profile_unselected,
                        selectedIconRes = R.drawable.nav_profile_selected,
                        onClick = onProfileClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun UrbanNavigationItem(
    text: String,
    selected: Boolean,
    @DrawableRes unselectedIconRes: Int,
    @DrawableRes selectedIconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor = if (selected) ActionBlue else AppTextMuted
    val iconRes = if (selected) selectedIconRes else unselectedIconRes

    Column(
        modifier = modifier
            .fillMaxHeight()
            .sizeIn(minWidth = 64.dp, minHeight = 56.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier
                .size(34.dp)
        )
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
