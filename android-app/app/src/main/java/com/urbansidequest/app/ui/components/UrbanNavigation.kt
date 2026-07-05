package com.urbansidequest.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.urbansidequest.app.R
import com.urbansidequest.app.ui.theme.AppBorder
import com.urbansidequest.app.ui.theme.AppSurface
import com.urbansidequest.app.ui.theme.AppTextMuted
import com.urbansidequest.app.ui.theme.DeepTeal

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
            .height(78.dp),
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
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                        text = "进行",
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
    val selectionSpec = tween<Float>(
        durationMillis = urbanMotionDuration(UrbanMotion.SelectionMillis),
        easing = FastOutSlowInEasing
    )
    val colorSpec = tween<Color>(
        durationMillis = urbanMotionDuration(UrbanMotion.SelectionMillis),
        easing = FastOutSlowInEasing
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) DeepTeal else AppTextMuted,
        animationSpec = colorSpec,
        label = "navigation_content_color"
    )
    val selectedProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = selectionSpec,
        label = "navigation_icon_crossfade"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = selectionSpec,
        label = "navigation_icon_scale"
    )
    val iconOffsetY by animateDpAsState(
        targetValue = if (selected) (-2).dp else 0.dp,
        animationSpec = tween(
            durationMillis = urbanMotionDuration(UrbanMotion.SelectionMillis),
            easing = FastOutSlowInEasing
        ),
        label = "navigation_icon_offset"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .offset(y = iconOffsetY)
                .size(29.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(unselectedIconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(1f - selectedProgress)
            )
            Image(
                painter = painterResource(selectedIconRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(selectedProgress)
            )
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}
