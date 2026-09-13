package de.v404.honorarcraft.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import de.v404.honorarcraft.shared.AndroidSettings
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.data.AndroidDatabase

@Composable
actual fun rememberMainViewModel(): MainViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                MainViewModel(
                    database = AndroidDatabase.getDatabase(context),
                    settings = AndroidSettings(context),
                ) as T
        }
    )
}
