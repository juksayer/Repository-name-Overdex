package com.example.overdex.battle.observation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.overdex.MainActivity
import com.example.overdex.R

/** Presentation only: never starts or stops observation. */
internal enum class DroidballNotificationState { IDLE, OBSERVING }

internal const val DROIDBALL_NOTIFICATION_ID = 197
internal const val DROIDBALL_CHANNEL_ID = "droidball_observation"
internal const val DROIDBALL_START_ACTION = "com.example.overdex.DROIDBALL_START"
internal const val DROIDBALL_STOP_ACTION = "com.example.overdex.DROIDBALL_STOP"
internal const val DROIDBALL_NEXT_ACTION = "com.example.overdex.DROIDBALL_NEXT"

internal enum class DroidballNotificationControl { START, STOP, NEXT_MATCH, OVERDEX }

internal fun droidballNotificationTargetsCurrentSession(
    requestedSessionId: String?, activeSessionId: String,
    requestedMatchId: String?, activeMatchId: String?,
): Boolean = !requestedSessionId.isNullOrBlank() && requestedSessionId == activeSessionId &&
    requestedMatchId == activeMatchId

/** Eye state describes observation; controls describe the actual capture lifecycle. */
internal fun droidballNotificationControls(
    sessionActive: Boolean,
    registrationMode: Boolean,
    matchId: String?,
): List<DroidballNotificationControl> = buildList {
    add(if (sessionActive) DroidballNotificationControl.STOP else DroidballNotificationControl.START)
    if (sessionActive && !registrationMode && matchId != null) add(DroidballNotificationControl.NEXT_MATCH)
    add(DroidballNotificationControl.OVERDEX)
}

internal fun createDroidballNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DROIDBALL_CHANNEL_ID, "Droidball Observation", NotificationManager.IMPORTANCE_LOW)
        )
    }
}

/** A dismissible launcher, not a foreground service or an active screen capture. */
internal fun showIdleDroidballNotification(context: Context) {
    if (DroidballService.isRunning()) return
    createDroidballNotificationChannel(context)
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return
    // Notification permission can be revoked between the availability check and notify.
    try {
        manager.notify(DROIDBALL_NOTIFICATION_ID, buildDroidballNotification(
            context, DROIDBALL_CHANNEL_ID, DroidballNotificationState.IDLE,
            registrationMode = false, sessionActive = false,
        ))
    } catch (_: SecurityException) { }
}

/** Explicit, non-exported actions cannot restart a service with an old projection token. */
class DroidballNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DroidballService.handleNotificationControl(
            intent.action, intent.getStringExtra("sessionId"), intent.getStringExtra("matchId")
        )
        if (!DroidballService.isRunning()) showIdleDroidballNotification(context)
    }
}

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
    sessionActive: Boolean = true,
    sessionId: String? = null,
    matchId: String? = null,
): Notification {
    val observing = state == DroidballNotificationState.OBSERVING
    val openApp = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val start = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).setAction(DROIDBALL_START_ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    fun serviceControl(action: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, DroidballNotificationReceiver::class.java).setAction(action)
            // Keep already-delivered controls tied to their original session/match.
            .setData(Uri.Builder().scheme("overdex").authority("droidball")
                .appendPath(sessionId ?: "none").appendPath(matchId ?: "none").appendPath(action).build())
            .putExtra("sessionId", sessionId).putExtra("matchId", matchId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(context, channelId)
        .setContentTitle(if (observing) "Droidball observing" else "Droidball idle")
        .setContentText(when {
            !sessionActive -> "Session stopped. Start when you are ready."
            registrationMode -> "Reading one Pokémon into an owned card."
            !observing -> "Session open. Stop to save, or prepare the next match."
            else -> "Battle observation in progress."
        })
        .setSmallIcon(if (observing) R.drawable.ic_droidball_observing else R.drawable.ic_droidball_idle)
        .setContentIntent(openApp)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(sessionActive)
        .setOnlyAlertOnce(true)
        .setSilent(true)
    droidballNotificationControls(sessionActive, registrationMode, matchId).forEach { control ->
        when (control) {
            DroidballNotificationControl.START -> builder.addAction(0, "Start", start)
            DroidballNotificationControl.STOP -> builder.addAction(0, "Stop", serviceControl(DROIDBALL_STOP_ACTION))
            DroidballNotificationControl.NEXT_MATCH -> builder.addAction(0, "Next match", serviceControl(DROIDBALL_NEXT_ACTION))
            DroidballNotificationControl.OVERDEX -> builder.addAction(0, "Overdex", openApp)
        }
    }
    return builder.build()
}
