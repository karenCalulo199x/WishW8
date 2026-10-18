package com.karencalulo.wishwait.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karencalulo.wishwait.WishWaitApp
import com.karencalulo.wishwait.di.AppContainer

/** Creates a ViewModel scoped to the current nav destination, wired from [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as WishWaitApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
