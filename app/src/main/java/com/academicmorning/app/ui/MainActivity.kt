package com.academicmorning.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.academicmorning.app.AcademicMorningApp
import com.academicmorning.app.ui.detail.PaperDetailScreen
import com.academicmorning.app.ui.discover.DiscoverScreen
import com.academicmorning.app.ui.favorites.FavoritesScreen
import com.academicmorning.app.ui.home.HomeScreen
import com.academicmorning.app.ui.onboarding.OnboardingScreen
import com.academicmorning.app.ui.settings.ApiConfigScreen
import com.academicmorning.app.ui.settings.SettingsScreen
import com.academicmorning.app.ui.stats.StatsScreen
import com.academicmorning.app.ui.theme.AcademicMorningTheme
import com.academicmorning.app.ui.theme.AmDivider
import com.academicmorning.app.ui.theme.AmPrimary
import com.academicmorning.app.ui.theme.AmTextSecondary

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val DISCOVER = "discover"
    const val FAVORITES = "favorites"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val API_CONFIG = "apiconfig"
    const val DETAIL = "detail/{paperId}"
    fun detail(id: String) = "detail/" + android.net.Uri.encode(id)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "首页", Icons.Rounded.Home),
    Tab(Routes.DISCOVER, "发现期刊", Icons.Rounded.Explore),
    Tab(Routes.FAVORITES, "收藏", Icons.Rounded.Star),
    Tab(Routes.STATS, "统计", Icons.Rounded.BarChart),
    Tab(Routes.SETTINGS, "设置", Icons.Rounded.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AcademicMorningTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as AcademicMorningApp
    val onboardingDone by app.container.settings.onboardingDone
        .collectAsStateWithLifecycle(initialValue = null)

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

    when (onboardingDone) {
        null -> { /* 加载中，保持空白 */ }
        else -> {
            Scaffold(
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, tab.label) },
                                    label = { Text(tab.label, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AmPrimary,
                                        selectedTextColor = AmPrimary,
                                        unselectedIconColor = AmTextSecondary,
                                        unselectedTextColor = AmTextSecondary,
                                        indicatorColor = AmDivider.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = if (onboardingDone == true) Routes.HOME else Routes.ONBOARDING,
                    modifier = Modifier.padding(padding)
                ) {
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onFinish = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Routes.HOME) {
                        HomeScreen(
                            onPaperClick = { navController.navigate(Routes.detail(it)) }
                        )
                    }
                    composable(Routes.DISCOVER) { DiscoverScreen() }
                    composable(Routes.FAVORITES) {
                        FavoritesScreen(
                            onPaperClick = { navController.navigate(Routes.detail(it)) }
                        )
                    }
                    composable(Routes.STATS) { StatsScreen() }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onOpenApiConfig = { navController.navigate(Routes.API_CONFIG) }
                        )
                    }
                    composable(Routes.API_CONFIG) {
                        ApiConfigScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        Routes.DETAIL,
                        arguments = listOf(navArgument("paperId") { type = NavType.StringType })
                    ) { entry ->
                        PaperDetailScreen(
                            paperId = android.net.Uri.decode(entry.arguments?.getString("paperId").orEmpty()),
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
