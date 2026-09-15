package com.linguaai.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

/** Bottom-bar entry: route class, label and filled/outlined icon pair. */
data class TopLevelDestination(
    val routeClass: KClass<*>,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomBarDestinations =
    listOf(
        TopLevelDestination(HomeRoute::class, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        TopLevelDestination(LearnRoute::class, "Learn", Icons.Filled.School, Icons.Outlined.School),
        TopLevelDestination(AiTutorRoute::class, "AI Tutor", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
        TopLevelDestination(ProgressRoute::class, "Progress", Icons.Filled.Insights, Icons.Outlined.Insights),
        TopLevelDestination(ProfileRoute::class, "Profile", Icons.Filled.Person, Icons.Outlined.Person),
    )
