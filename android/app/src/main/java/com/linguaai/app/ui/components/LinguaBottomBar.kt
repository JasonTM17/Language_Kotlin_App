package com.linguaai.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.linguaai.app.ui.navigation.TopLevelDestination
import com.linguaai.app.ui.navigation.bottomBarDestinations
import kotlin.reflect.KClass

/** Bottom navigation shown only on top-level destinations. */
@Composable
fun LinguaBottomBar(
    destinations: List<TopLevelDestination> = bottomBarDestinations,
    currentDestination: NavDestination?,
    onNavigate: (KClass<*>) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        destinations.forEach { destination ->
            val selected =
                currentDestination != null &&
                    currentDestination.hasRoute(destination.routeClass)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination.routeClass) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            )
        }
    }
}
