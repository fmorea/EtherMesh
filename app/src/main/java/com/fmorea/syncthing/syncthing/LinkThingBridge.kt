package com.fmorea.syncthing.syncthing

import androidx.compose.ui.platform.ComposeView

import com.fmorea.syncthing.theme.ApplicationTheme

object LinkThingBridge {
    @JvmStatic
    fun setContent(view: ComposeView, viewModel: LinkThingViewModel, scannedDeviceId: String = "") {
        view.setContent {
            ApplicationTheme {
                LinkThingScreen(viewModel, scannedDeviceId)
            }
        }
    }
}
