package com.example.overdex.battle.observation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.overdex.model.AnchorRegion
import kotlin.math.roundToInt

/** One ephemeral badge image, at the source coordinates, only while GO hides its badges.
 * It is presentation, never a new observation. Call [exclusion] at capture time.
 */
internal class RetainedOpponentBadge(context: Context, private val windows: WindowManager) {
    private val main = Handler(Looper.getMainLooper())
    private val visibility = RetainedBadgeVisibility()
    private var cached: Bitmap? = null
    private var region: AnchorRegion? = null
    private var displayWidth = 0
    private var displayHeight = 0
    @Volatile private var wanted = false
    @Volatile private var rendered = false
    @Volatile private var clearAfter = Long.MIN_VALUE
    @Volatile private var closed = false
    private val view = object : View(context) {
        var image: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun onDraw(canvas: Canvas) {
            val image = image ?: return
            val r = width * 27f / 415f
            val shape = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, Path.Direction.CW) }
            val save = canvas.save()
            canvas.clipPath(shape)
            canvas.drawBitmap(image, null, Rect(0, 0, width, height), paint)
            canvas.restoreToCount(save)
        }
    }

    @Synchronized fun exclusion(at: Long): AnchorRegion? =
        region.takeIf { wanted || rendered || at < clearAfter }

    @Synchronized fun update(bitmap: Bitmap, at: Long, playerPresent: Boolean, opponentPresent: Boolean,
                             badgeRegion: AnchorRegion, width: Int, height: Int,
                             observationScope: RetainedBadgeScope?, battleSurfaceEstablished: Boolean,
                             hudRegion: AnchorRegion?) {
        if (closed) return
        val decision = visibility.update(at, playerPresent, opponentPresent, exclusion(at) != null,
            observationScope, battleSurfaceEstablished,
            sourceOverlapsHud = !SpeciesCaptureVisibility.canRead(badgeRegion, hudRegion))
        if (decision.discardOriginal) cached = null
        if (decision.cacheOriginal) {
            val bounds = BattleCropResolver.resolveRect(badgeRegion, bitmap.width, bitmap.height) ?: return
            // Keep only one small image, not a history of frames or PNG artifacts.
            cached = Bitmap.createBitmap(bitmap, bounds.left, bounds.top, bounds.width(), bounds.height())
            region = badgeRegion
            displayWidth = width; displayHeight = height
        }
        if (wanted != decision.showRetained) {
            wanted = decision.showRetained
            main.post(::render)
        }
    }

    @Synchronized fun reset() {
        visibility.reset()
        cached = null
        wanted = false
        main.post(::render)
    }

    /** UI thread only; setFitInsetsTypes(0) keeps these full-display coordinates. */
    @Synchronized private fun render() {
        if (!wanted || closed) {
            if (view.isAttachedToWindow) windows.removeViewImmediate(view)
            if (rendered) clearAfter = System.nanoTime() + 250_000_000L
            rendered = false
            view.image = null
            return
        }
        val area = region ?: return
        view.image = cached ?: return
        if (view.isAttachedToWindow) return
        val params = WindowManager.LayoutParams(
            (area.width * displayWidth).roundToInt(), (area.height * displayHeight).roundToInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (area.x * displayWidth).roundToInt(); y = (area.y * displayHeight).roundToInt()
            // Pass touches through on Android 12+, including the native badge beneath.
            alpha = .8f
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
        }
        try {
            windows.addView(view, params)
            rendered = true
            // Account for OEM window origins without changing any saved calibration.
            view.post {
                if (view.isAttachedToWindow) {
                    val location = IntArray(2); view.getLocationOnScreen(location)
                    val dx = (area.x * displayWidth).roundToInt() - location[0]
                    val dy = (area.y * displayHeight).roundToInt() - location[1]
                    if (dx != 0 || dy != 0) {
                        params.x += dx; params.y += dy
                        windows.updateViewLayout(view, params)
                    }
                }
            }
        } catch (error: Exception) {
            wanted = false
            visibility.reset()
            Log.w("RetainedOpponentBadge", "Could not show retained badge", error)
        }
    }

    @Synchronized fun close() { closed = true; reset() }
}
