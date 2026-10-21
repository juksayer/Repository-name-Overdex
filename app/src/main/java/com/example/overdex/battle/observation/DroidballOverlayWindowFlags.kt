package com.example.overdex.battle.observation

import android.view.WindowManager

/** SECURE occludes underlying GO pixels in whole-display capture rather than omitting the HUD. */
internal fun droidballOverlayWindowFlags(registrationMode: Boolean): Int =
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        (if (registrationMode) WindowManager.LayoutParams.FLAG_SECURE else 0)
