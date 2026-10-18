package com.karencalulo.wishwait.presentation.navigation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.karencalulo.wishwait.presentation.addwish.SharedText
import androidx.annotation.StringRes
import com.karencalulo.wishwait.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karencalulo.wishwait.presentation.addwish.AddWishRoute
import com.karencalulo.wishwait.presentation.calculator.CalculatorRoute
import com.karencalulo.wishwait.presentation.common.Fmt
import com.karencalulo.wishwait.presentation.detail.WishDetailRoute
import com.karencalulo.wishwait.presentation.home.HomeRoute
import com.karencalulo.wishwait.presentation.onboarding.OnboardingRoute
import com.karencalulo.wishwait.presentation.settings.SettingsRoute
import com.karencalulo.wishwait.presentation.settings.TermsScreen
import com.karencalulo.wishwait.presentation.tips.TipJarRoute
import com.karencalulo.wishwait.presentation.wins.WinsRoute

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CALCULATOR = "calculator?price={price}"
    const val WINS = "wins"
    const val ADD = "add"
    const val ADD_SHARED = "add_shared"
    const val DETAIL = "wish/{id}"
    const val SETTINGS = "settings"
    const val TERMS = "terms"
    const val TIPS = "tips"

    fun calculator(price: Double? = null) = if (price == null) "calculator" else "calculator?price=${Fmt.plain(price)}"
    fun detail(id: String) = "wish/$id"
}

private enum class Tab(val route: String, val navRoute: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    WISHES(Routes.HOME, Routes.HOME, R.string.tab_wishes, Icons.Rounded.HourglassTop),
    CALCULATOR(Routes.CALCULATOR, "calculator", R.string.tab_calculator, Icons.Rounded.Calculate),
    WINS(Routes.WINS, Routes.WINS, R.string.tab_wins, Icons.Rounded.EmojiEvents),
}

@Composable
fun WishWaitNavHost(
    onboarded: Boolean,
    pendingWishId: String?,
    onPendingConsumed: () -> Unit,
    pendingShare: SharedText? = null,
    onShareConsumed: () -> Unit = {},
    pendingAdd: Boolean = false,
    onAddConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val start = remember { if (onboarded) Routes.HOME else Routes.ONBOARDING }
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = Tab.entries.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    // "Share → WishWait": open the add flow pre-filled. Waits until onboarding is done.
    var activeShare by remember { mutableStateOf<SharedText?>(null) }
    LaunchedEffect(pendingShare, onboarded) {
        val share = pendingShare ?: return@LaunchedEffect
        if (!onboarded) return@LaunchedEffect
        activeShare = share
        onShareConsumed()
        navController.navigate(Routes.ADD_SHARED) {
            popUpTo(Routes.HOME)
            launchSingleTop = false
        }
    }

    // Widget "Ask Ollie first" → straight to the add flow.
    LaunchedEffect(pendingAdd, onboarded) {
        if (!pendingAdd || !onboarded) return@LaunchedEffect
        onAddConsumed()
        navController.navigate(Routes.ADD) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    // Notification tap → open that wish.
    LaunchedEffect(pendingWishId, onboarded) {
        val id = pendingWishId ?: return@LaunchedEffect
        if (onboarded) navController.navigate(Routes.detail(id)) { launchSingleTop = true }
        onPendingConsumed()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.navRoute) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    onFinished = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeRoute(
                    onAddWish = { navController.navigate(Routes.ADD) },
                    onOpenWish = { navController.navigate(Routes.detail(it)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(
                Routes.CALCULATOR,
                arguments = listOf(
                    navArgument("price") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                CalculatorRoute(initialPrice = entry.arguments?.getString("price")?.toDoubleOrNull())
            }
            composable(Routes.WINS) {
                WinsRoute(onOpenWish = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.ADD_SHARED) {
                AddWishRoute(
                    shared = activeShare,
                    onClose = { navController.popBackStack() },
                    onOpenWish = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.ADD_SHARED) { inclusive = true }
                        }
                    },
                    onOpenCalculator = { price -> navController.navigate(Routes.calculator(price)) },
                )
            }
            composable(Routes.ADD) {
                AddWishRoute(
                    onClose = { navController.popBackStack() },
                    onOpenWish = { id ->
                        navController.navigate(Routes.detail(id)) {
                            popUpTo(Routes.ADD) { inclusive = true }
                        }
                    },
                    onOpenCalculator = { price -> navController.navigate(Routes.calculator(price)) },
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                WishDetailRoute(
                    wishId = id,
                    onBack = {
                        if (!navController.popBackStack()) navController.navigate(Routes.HOME)
                    },
                    onOpenCalculator = { price -> navController.navigate(Routes.calculator(price)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onResetDone = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onOpenTerms = { navController.navigate(Routes.TERMS) { launchSingleTop = true } },
                    onOpenTips = { navController.navigate(Routes.TIPS) { launchSingleTop = true } },
                )
            }
            composable(Routes.TERMS) {
                TermsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.TIPS) {
                TipJarRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
