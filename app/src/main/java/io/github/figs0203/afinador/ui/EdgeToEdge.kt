package io.github.figs0203.afinador.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Draws the content behind the system bars and pads it by the real insets, so
 * every screen of the songbook matches the tuner's edge-to-edge look.
 */
fun Activity.applyEdgeToEdge(
    root: View,
    horizontal: Int = 20,
    top: Int = 14,
    bottom: Int = 10
) {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
    }

    root.setOnApplyWindowInsetsListener { view, insets ->
        val topInset: Int
        val bottomInset: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
        } else {
            @Suppress("DEPRECATION")
            topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottomInset = insets.systemWindowInsetBottom
        }
        view.setPadding(dp(horizontal), topInset + dp(top), dp(horizontal), bottomInset + dp(bottom))
        insets
    }
    root.requestApplyInsets()
}
