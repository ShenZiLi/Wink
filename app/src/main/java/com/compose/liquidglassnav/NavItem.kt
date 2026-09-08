package com.compose.liquidglassnav

import androidx.compose.ui.graphics.vector.ImageVector


data class NavItem(
    val icon: ImageVector,
    val activeIcon: ImageVector? = null, // Optional active state icon
    val label: String,
    val route: String = "",
    val badge: Int? = null // Optional badge count
)