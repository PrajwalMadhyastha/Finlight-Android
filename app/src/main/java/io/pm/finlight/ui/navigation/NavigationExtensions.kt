package io.pm.finlight.ui.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import io.pm.finlight.di.FinlightViewModelFactory
import io.pm.finlight.utils.findActivity

@Composable
inline fun <reified VM : ViewModel> resolveActivityViewModel(): VM {
    val context = LocalContext.current
    val owner =
        context.findActivity()
            ?: LocalViewModelStoreOwner.current
            ?: error("No ViewModelStoreOwner available")
    val factory = FinlightViewModelFactory.create(context.applicationContext as Application)
    return viewModel(
        viewModelStoreOwner = owner,
        factory = factory,
    )
}

val standardEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = {
    fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(300))
}

val standardExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = {
    fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(300))
}

val standardPopEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition) = {
    fadeIn(animationSpec = tween(300)) + slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(300))
}

val standardPopExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition) = {
    fadeOut(animationSpec = tween(300)) + slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(300))
}
