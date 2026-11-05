package com.example.overdex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/** Detail-to-module returns keep their search; reaching the directory ends it. */
@Composable
internal fun DirectorySearchResetEffect(
    currentRoute: String?,
    onResetSearches: () -> Unit,
) {
    val reset by rememberUpdatedState(onResetSearches)
    LaunchedEffect(currentRoute) {
        if (currentRoute == "main_menu") reset()
    }
}
