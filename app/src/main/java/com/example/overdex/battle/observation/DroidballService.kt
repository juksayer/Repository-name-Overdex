package com.example.overdex.battle.observation

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.overdex.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.util.Log
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overdex.ui.components.BattleOverlay

/**
 * The technical infrastructure layer for the ODX-FI.
 * 
 * DroidballService manages:
 * 1. MediaProjection (Screen Capture)
 * 2. WindowManager Overlay (Field Presentation)
 * 3. Foreground Lifecycle (Required for persistent capture)
 */
class DroidballService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val NOTIFICATION_ID = 197
        private const val CHANNEL_ID = "droidball_observation"
        
        private val _facts = MutableSharedFlow<DroidballFact>(extraBufferCapacity = 1)
        val facts = _facts.asSharedFlow()

        private val _frames = MutableSharedFlow<Bitmap>(extraBufferCapacity = 1)
        val frames = _frames.asSharedFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, DroidballService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DroidballService::class.java))
        }

        /**
         * The single publication API for Battle facts.
         */
        fun emitFact(fact: DroidballFact) {
            _facts.tryEmit(fact)
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("DroidballService", "MediaProjection stopped by system")
            stopSelf()
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Lifecycle requirements for Compose in Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == Activity.RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            setupMediaProjection(resultCode, data)
            setupOverlay()
            _facts.tryEmit(DroidballFact.Started)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)
        
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                
                // Convert Image to Bitmap and emit
                // Optimization: In a real implementation, we'd reuse buffers.
                // For Git #197 milestone, we just prove the flow.
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                
                val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                
                _frames.tryEmit(bitmap)
                _facts.tryEmit(DroidballFact.FrameCaptured)
                
                image.close()
            }, null)
        }

        mediaProjection?.createVirtualDisplay(
            "DroidballCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )
    }

    private fun setupOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        overlayView = ComposeView(this).apply {
            setContent {
                BattleOverlay()
            }
        }
        
        // Essential for Compose in WindowManager
        overlayView!!.setViewTreeLifecycleOwner(this)
        overlayView!!.setViewTreeViewModelStoreOwner(this)
        overlayView!!.setViewTreeSavedStateRegistryOwner(this)
        
        windowManager.addView(overlayView, params)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Droidball Observation",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ODX-FI Active")
            .setContentText("Continuous observation in progress.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Placeholder
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        _facts.tryEmit(DroidballFact.Stopped)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        overlayView?.let { windowManager.removeView(it) }
        imageReader?.close()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

sealed class DroidballFact {
    object Started : DroidballFact()
    object Stopped : DroidballFact()
    object FrameCaptured : DroidballFact()
    data class Error(val message: String) : DroidballFact()
    data class CountdownWitnessed(val value: String) : DroidballFact()
}
