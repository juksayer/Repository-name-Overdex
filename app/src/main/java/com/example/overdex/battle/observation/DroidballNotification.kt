package com.example.overdex.battle.observation

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.overdex.MainActivity
import com.example.overdex.R

/** Presentation only: never starts or stops observation. */
internal enum class DroidballNotificationState { IDLE, OBSERVING }

internal fun droidballNotificationState(
    runtime: DroidballRuntimeState,
    instrumentVisible: Boolean,
    mode: DroidballOverlayMode,
    registrationMode: Boolean,
): DroidballNotificationState {
    if (runtime != DroidballRuntimeState.ACTIVE || instrumentVisible) {
        return DroidballNotificationState.IDLE
    }
    return when {
        registrationMode -> DroidballNotificationState.OBSERVING
        mode == DroidballOverlayMode.PRE_BATTLE || mode == DroidballOverlayMode.RESULT ->
            DroidballNotificationState.IDLE
        else -> DroidballNotificationState.OBSERVING
    }
}

internal fun buildDroidballNotification(
    context: Context,
    channelId: String,
    state: DroidballNotificationState,
    registrationMode: Boolean,
): Notification {
    val observing = state == DroidballNotificationState.OBSERVING
    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, channelId)
        .setContentTitle(if (observing) "Droidball observing" else "Droidball idle")
        .setContentText(when {
            !observing -> "Ready when you are. Tap to return to Overdex."
            registrationMode -> "Reading one Pokémon into an owned card."
            else -> "Battle observation in progress."
        })
        .setSmallIcon(if (observing) R.drawable.ic_droidball_observing else R.drawable.ic_droidball_idle)
        .setContentIntent(openApp)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
}
