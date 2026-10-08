package com.example.overdex.battle.observation

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.overdex.R
import com.example.overdex.battle.audio.AudioCaptureCue
import com.example.overdex.battle.audio.CueCenteredPcmCollector
import com.example.overdex.battle.audio.BattleCryCueKind
import com.example.overdex.model.observation.CapturedAudioFrame
import com.example.overdex.model.observation.CapturedDeviceMotionPulse
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.ui.components.BattleOverlay
import com.example.overdex.ui.components.BattleHudOverlayGeometry
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Capture diagnostics data class for the temporary debug HUD.
 */
data class CaptureDiagnostics(
    val state: String = "NOT OBSERVED",
    val width: Int? = null,
    val height: Int? = null,
    val publicationNanoTime: Long? = null
)

/**
 * Process-local truth for the foreground capture service.
 *
 * Activity and navigation state may be recreated while Pokemon GO owns the
 * screen. Controls must read this state instead of assuming a newly-created
 * ViewModel means Droidball is docked.
 */
enum class DroidballRuntimeState {
    STOPPED,
    STARTING,
    ACTIVE
}

/**
 * The technical infrastructure layer for the ODX-FI.
 * 
 * DroidballService manages:
 * 1. MediaProjection (Screen Capture)
 * 2. WindowManager Overlay (Field Presentation)
 * 3. Foreground Lifecycle (Required for persistent capture)
 */
class DroidballService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner,
    SensorEventListener {

    companion object {
        private const val NOTIFICATION_ID = 197
        private const val CHANNEL_ID = "droidball_observation"
        /**
         * Battle regions are normalized, so their calibration remains valid at this
         * capture scale.  Keeping the source at three quarters of display resolution
         * leaves the 60 px species strips at 45 px while substantially reducing the
         * native bitmap pressure that can make Android reclaim the app on return.
         */
        // The narrowest calibrated text strips are about 60 px high on the
        // published 1080x2400 frame.  0.625 keeps them above the 32 px OCR
        // floor while reducing each transient source bitmap by roughly 30%.
        private const val CAPTURE_SCALE = 0.625f
        private const val MIN_CAPTURE_INTERVAL_NANOS = 50_000_000L // 20 fps
        @Volatile private var activeService: DroidballService? = null

        private val _runtimeState = MutableStateFlow(DroidballRuntimeState.STOPPED)
        val runtimeState = _runtimeState.asStateFlow()

        fun isRunning(): Boolean = _runtimeState.value != DroidballRuntimeState.STOPPED
        
        private val _signals = MutableSharedFlow<DroidballSignal>(extraBufferCapacity = 64)
        val signals = _signals.asSharedFlow()

        private val _frames = MutableSharedFlow<CapturedVisualFrame>(
            // A published frame owns a large native Bitmap.  The capture stream
            // is a live observation source, so retaining a queue of frames for
            // slow crop workers only increases memory pressure and makes Android
            // destroy the activity while Pokémon GO is in the foreground.  Each
            // worker can safely skip an intermediate frame because every accepted
            // crop is persisted with its own capture timestamp.
            replay = 1,
            extraBufferCapacity = 0,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val frames = _frames.asSharedFlow()

        private val _audioFrames = MutableSharedFlow<CapturedAudioFrame>(
            replay = 0,
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        /** Raw audio snippets, published independently from captured visual frames. */
        val audioFrames = _audioFrames.asSharedFlow()

        private val _motionPulses = MutableSharedFlow<CapturedDeviceMotionPulse>(
            replay = 0,
            extraBufferCapacity = 16,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        /** Short device-motion bursts, before any haptic or charge-move interpretation. */
        val motionPulses = _motionPulses.asSharedFlow()

        private val _motionPulseCaptureAvailable = MutableStateFlow<Boolean?>(null)
        val motionPulseCaptureAvailable = _motionPulseCaptureAvailable.asStateFlow()

        private val _audioInputStatus = MutableStateFlow(
            com.example.overdex.battle.custody.AudioInputStatus("NONE", "OFFLINE"))
        val audioInputStatus = _audioInputStatus.asStateFlow()
        private val _audioCaptureAvailable = MutableStateFlow<Boolean?>(null)
        /** Null until a deployment attempts audio startup; false means it ceased operating. */
        val audioCaptureAvailable = _audioCaptureAvailable.asStateFlow()

        private val _captureDiagnostics = MutableStateFlow(CaptureDiagnostics(state = "NOT OBSERVED"))
        val captureDiagnostics = _captureDiagnostics.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            _runtimeState.value = DroidballRuntimeState.STARTING
            val intent = Intent(context, DroidballService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (error: Throwable) {
                _runtimeState.value = DroidballRuntimeState.STOPPED
                throw error
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DroidballService::class.java))
        }

        /** Moves the visible field control without changing its observation state. */
        fun moveOverlayBy(deltaX: Float, deltaY: Float) {
            activeService?.moveOverlayBy(deltaX, deltaY)
        }

        /** Settles the field control against the nearer display edge. */
        fun snapOverlayToNearestEdge() {
            activeService?.snapOverlayToNearestEdge()
        }

        /** Requests a short microphone artifact around an already-accepted visual article. */
        fun requestCueCenteredAudio(articleId: String, cueKind: BattleCryCueKind) {
            activeService?.requestCueCenteredAudio(AudioCaptureCue(articleId, cueKind))
        }

        /**
         * The single publication API for instrument signals.
         */
        fun emitSignal(signal: DroidballSignal) {
            val delivered = _signals.tryEmit(signal)
            Log.d("DROIDBALL_SIGNAL", "published=${signal::class.simpleName} delivered=$delivered subscribers=${_signals.subscriptionCount.value}")
        }
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayAnchoredToBattleHud = false
    private var freeOverlayX: Int? = null
    private var freeOverlayY: Int? = null
    private lateinit var battleHudLayoutStore: BattleHudLayoutStore
    
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var audioRecord: AudioRecord? = null
    private var audioCaptureJob: kotlinx.coroutines.Job? = null
    private var sensorManager: SensorManager? = null
    private var linearAccelerationSensor: Sensor? = null
    private val motionPulseDetector = DeviceMotionPulseDetector()
    private val audioCollectorLock = Any()
    // 500 ms pre-roll and 700 ms post-roll at 48 kHz mono PCM-16.
    private val cueCenteredAudio = CueCenteredPcmCollector(preRollBytes = 48_000, postRollBytes = 67_200)
    private var firstFrameLogged = false
    private var lastPublishedFrameNanos: Long? = null

    private var publicationAttempts = 0L
    private var successEmitCount = 0L
    private var rejectedEmitCount = 0L
    private var deliveryLoggingJob: kotlinx.coroutines.Job? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("DroidballService", "MediaProjection stopped by system")
            markStopped()
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.d("ODX_CAPTURE_GEOMETRY", "onCapturedContentResize: captured-content dimensions: ${width}x${height}")
        }
    }

    private fun markStopped() {
        val current = _captureDiagnostics.value
        _captureDiagnostics.value = current.copy(state = "STOPPED")
        _runtimeState.value = DroidballRuntimeState.STOPPED
        DroidballRuntimeMarker.markStopped(this)
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
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] DroidballService.onCreate()")
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        battleHudLayoutStore = BattleHudLayoutStore(this)
        activeService = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] DroidballService.onStartCommand()")
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == Activity.RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                startForeground(NOTIFICATION_ID, createNotification(), serviceTypes)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            runCatching {
                setupMediaProjection(resultCode, data)
                setupOverlay()
                startAudioCapture()
                runCatching { startMotionPulseCapture() }.onFailure { error ->
                    _motionPulseCaptureAvailable.value = false
                    Log.w("DEVICE_MOTION", "Unable to start optional motion-pulse capture", error)
                }
                _captureDiagnostics.value = CaptureDiagnostics(state = "READY", width = null, height = null, publicationNanoTime = null)
                _runtimeState.value = DroidballRuntimeState.ACTIVE
                DroidballRuntimeMarker.markActive(this)
                _signals.tryEmit(DroidballSignal.Started)
            }.onFailure { error ->
                Log.e("DROIDBALL_LAUNCH", "Unable to initialize Droidball service", error)
                _signals.tryEmit(DroidballSignal.Error("Unable to initialize Droidball: ${error.message ?: error::class.simpleName}"))
                stopSelf()
            }
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startMotionPulseCapture() {
        motionPulseDetector.reset()
        val manager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager = manager
        linearAccelerationSensor = sensor
        if (sensor == null) {
            _motionPulseCaptureAvailable.value = false
            Log.w("DEVICE_MOTION", "Linear-acceleration sensor unavailable; haptic evidence is offline")
            return
        }
        val registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        _motionPulseCaptureAvailable.value = registered
        Log.i("DEVICE_MOTION", "Linear-acceleration pulse capture registered=$registered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LINEAR_ACCELERATION || event.values.size < 3) return
        motionPulseDetector.accept(
            monotonicTimeNanos = System.nanoTime(),
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            wallTimeMillis = System.currentTimeMillis()
        )?.let { pulse ->
            val delivered = _motionPulses.tryEmit(pulse)
            Log.d(
                "DEVICE_MOTION",
                "pulse duration=${pulse.durationNanos} peak=${pulse.peakLinearAccelerationMetersPerSecondSquared} " +
                    "samples=${pulse.sampleCount} delivered=$delivered"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        firstFrameLogged = false
        publicationAttempts = 0L
        successEmitCount = 0L
        rejectedEmitCount = 0L
        deliveryLoggingJob?.cancel()
        deliveryLoggingJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5000L)
                val subs = _frames.subscriptionCount.value
                Log.d("FRAME_DELIVERY", "Cumulative Summary [5s]: attempts=$publicationAttempts, emitSucceeded=$successEmitCount, rejected=$rejectedEmitCount, subscribers=$subs")
            }
        }

        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)
        
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val maxBounds = windowManager.maximumWindowMetrics.bounds
            maxBounds.width() to maxBounds.height()
        } else {
            val realMetrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(realMetrics)
            realMetrics.widthPixels to realMetrics.heightPixels
        }
        val density = resources.displayMetrics.densityDpi
        // RGBA ImageReader rows are commonly aligned to 64 bytes (16 pixels).
        // Choosing an aligned width avoids allocating a second padded bitmap on
        // every frame. Derive height from the resulting uniform scale so the
        // published frame keeps the physical display's aspect ratio.
        val captureWidth = ((width * CAPTURE_SCALE).toInt() / 16 * 16).coerceAtLeast(16)
        val actualCaptureScale = captureWidth.toFloat() / width.toFloat()
        val captureHeight = (height * actualCaptureScale).roundToInt().coerceAtLeast(1)

        Log.d("ODX_CAPTURE_GEOMETRY", "setupMediaProjection: app-content metrics (system bars excluded): ${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentBounds = windowManager.currentWindowMetrics.bounds
            Log.d("ODX_CAPTURE_GEOMETRY", "setupMediaProjection: currentWindowMetrics.bounds: ${currentBounds.width()}x${currentBounds.height()}")
            Log.d("ODX_CAPTURE_GEOMETRY", "setupMediaProjection: maximumWindowMetrics.bounds: ${width}x${height}")
        } else {
            Log.d("ODX_CAPTURE_GEOMETRY", "setupMediaProjection: display.getRealMetrics: ${width}x${height}")
        }
        Log.d("ODX_CAPTURE_GEOMETRY", "setupMediaProjection: Requested ImageReader/VirtualDisplay: ${captureWidth}x${captureHeight} (targetScale=$CAPTURE_SCALE actualScale=$actualCaptureScale)")

        val frameHandler = captureHandler ?: HandlerThread("DroidballFrameCapture").let { thread ->
            thread.start()
            captureThread = thread
            Handler(thread.looper).also { captureHandler = it }
        }
        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val receivedAtNanos = System.nanoTime()
                    // Do not allocate a full source bitmap while the service is alive
                    // but the session has no observing consumers (for example during
                    // an Android activity recreation).
                    if (_frames.subscriptionCount.value == 0) return@setOnImageAvailableListener
                    if (lastPublishedFrameNanos?.let { receivedAtNanos - it < MIN_CAPTURE_INTERVAL_NANOS } == true) {
                        return@setOnImageAvailableListener
                    }
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val imgWidth = image.width
                    val imgHeight = image.height
                    val rowPadding = rowStride - (imgWidth * pixelStride)
                    
                    if (!firstFrameLogged) {
                        Log.d("ODX_CAPTURE_GEOMETRY", "First Frame acquired:")
                        Log.d("ODX_CAPTURE_GEOMETRY", "├── Image: ${imgWidth}x${imgHeight} | CropRect: ${image.cropRect}")
                        Log.d("ODX_CAPTURE_GEOMETRY", "├── Buffer: pixelStride=$pixelStride, rowStride=$rowStride")
                        Log.d("ODX_CAPTURE_GEOMETRY", "└── Calculated rowPadding=$rowPadding")
                    }

                    val bitmap = if (rowPadding > 0) {
                        val paddedWidth = imgWidth + rowPadding / pixelStride
                        val paddedBitmap = Bitmap.createBitmap(paddedWidth, imgHeight, Bitmap.Config.ARGB_8888)
                        try {
                            paddedBitmap.copyPixelsFromBuffer(buffer)
                            Bitmap.createBitmap(paddedBitmap, 0, 0, imgWidth, imgHeight)
                        } finally {
                            paddedBitmap.recycle()
                        }
                    } else {
                        val cleanBitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888)
                        cleanBitmap.copyPixelsFromBuffer(buffer)
                        cleanBitmap
                    }

                    if (!firstFrameLogged) {
                        Log.d("ODX_CAPTURE_GEOMETRY", "└── Final Published Bitmap: ${bitmap.width}x${bitmap.height}")
                        firstFrameLogged = true
                    }

                    val capturedAtWallTimeMillis = System.currentTimeMillis()
                    val capturedAtMonotonicTimeNanos = receivedAtNanos
                    lastPublishedFrameNanos?.let { previous ->
                        val gap = capturedAtMonotonicTimeNanos - previous
                        if (gap > 750_000_000L) _signals.tryEmit(DroidballSignal.VisualCaptureGap(gap))
                    }
                    lastPublishedFrameNanos = capturedAtMonotonicTimeNanos
                    val frame = CapturedVisualFrame(
                        bitmap = bitmap,
                        capturedAtWallTimeMillis = capturedAtWallTimeMillis,
                        capturedAtMonotonicTimeNanos = capturedAtMonotonicTimeNanos
                    )
                    publicationAttempts++
                    val emitSucceeded = _frames.tryEmit(frame)
                    if (emitSucceeded) {
                        successEmitCount++
                    } else {
                        rejectedEmitCount++
                    }
                    _signals.tryEmit(DroidballSignal.FrameCaptured)
                    _captureDiagnostics.value = CaptureDiagnostics(
                        state = "OBSERVING",
                        width = bitmap.width,
                        height = bitmap.height,
                        publicationNanoTime = capturedAtMonotonicTimeNanos
                    )
                } catch (e: Exception) {
                    Log.e("DroidballService", "Error processing captured frame", e)
                } finally {
                    image.close()
                }
            }, frameHandler)
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "DroidballCapture",
            captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )
    }

    private fun requestCueCenteredAudio(cue: AudioCaptureCue) {
        synchronized(audioCollectorLock) { cueCenteredAudio.cue(cue) }
    }

    /** Prefer game-only playback. A silent permitted stream stays explicitly silent. */
    private fun startAudioCapture() {
        fun status(source: String, state: String) {
            _audioInputStatus.value = com.example.overdex.battle.custody.AudioInputStatus(source, state)
            Log.i("ODX_AUDIO_INPUT", "source=$source state=$state")
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _audioCaptureAvailable.value = false
            status("NONE", "PERMISSION_UNAVAILABLE")
            return
        }
        val sampleRateHz = 48_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minimumBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minimumBuffer <= 0) {
            _audioCaptureAvailable.value = false
            status("NONE", "FORMAT_UNAVAILABLE")
            return
        }
        val bufferSize = maxOf(minimumBuffer, sampleRateHz / 5 * 2)
        var source = "PLAYBACK_POKEMON_GO"
        fun startRecord(create: () -> AudioRecord): AudioRecord? {
            var candidate: AudioRecord? = null
            return try {
                candidate = create()
                check(candidate.state == AudioRecord.STATE_INITIALIZED)
                candidate.startRecording()
                check(candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                candidate
            } catch (error: Exception) {
                try { candidate?.release() } catch (_: Exception) { }
                Log.w("ODX_AUDIO_INPUT", "Could not start $source", error)
                null
            }
        }
        val playback = startRecord {
            val uid = packageManager.getApplicationInfo("com.nianticlabs.pokemongo", 0).uid
            val capture = AudioPlaybackCaptureConfiguration.Builder(requireNotNull(mediaProjection))
                .addMatchingUid(uid)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRateHz)
                    .setChannelMask(channelConfig).setEncoding(encoding).build())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(capture)
                .build()
        }
        val record = playback ?: run {
            status(source, "START_FAILED")
            source = "MICROPHONE"
            startRecord { AudioRecord(MediaRecorder.AudioSource.MIC, sampleRateHz, channelConfig, encoding, bufferSize) }
        }
        if (record == null) {
            _audioCaptureAvailable.value = false
            status(source, "START_FAILED")
            return
        }
        audioRecord = record
        _audioCaptureAvailable.value = true
        status(source, "RUNNING_AWAITING_SIGNAL")
        audioCaptureJob = serviceScope.launch(Dispatchers.IO) {
            // A short read keeps the rolling buffer responsive to visual cues.
            val readBuffer = ByteArray(sampleRateHz / 50 * 2) // 20 ms
            var lastSignalAt = System.nanoTime()
            var lastState = "RUNNING_AWAITING_SIGNAL"
            try {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val count = record.read(readBuffer, 0, readBuffer.size)
                    if (count <= 0) { status(source, "READ_FAILED"); break }
                    val peak = com.example.overdex.battle.audio.PcmSignalLevel.peak(readBuffer, count)
                    val now = System.nanoTime()
                    if (peak >= 0.001f) lastSignalAt = now
                    val state = when {
                        peak >= 0.001f -> "SIGNAL_PRESENT"
                        now - lastSignalAt >= 2_000_000_000L -> "QUIET_OR_UNAVAILABLE"
                        else -> lastState
                    }
                    if (state != lastState) { status(source, state); lastState = state }
                    val completed = synchronized(audioCollectorLock) {
                        cueCenteredAudio.ingest(readBuffer.copyOf(count))
                    }
                    completed.forEach { capture ->
                        val completedAtNanos = System.nanoTime()
                        val durationNanos = capture.pcm16le.size.toLong() * 1_000_000_000L / (sampleRateHz * 2L)
                        _audioFrames.tryEmit(CapturedAudioFrame(
                            pcm16le = capture.pcm16le,
                            sampleRateHz = sampleRateHz,
                            channelCount = 1,
                            capturedAtWallTimeMillis = System.currentTimeMillis() - durationNanos / 1_000_000L,
                            capturedAtMonotonicTimeNanos = completedAtNanos - durationNanos,
                            cueArticleId = capture.cue.articleId,
                            cueKind = capture.cue.kind,
                            captureSource = source,
                            peakAmplitude = com.example.overdex.battle.audio.PcmSignalLevel.peak(capture.pcm16le)
                        ))
                    }
                }
            } catch (error: Exception) {
                Log.e("ODX_AUDIO_INPUT", "Capture failed: $source", error)
            } finally {
                try { record.stop() } catch (_: Exception) { }
                record.release()
                if (audioRecord === record) {
                    audioRecord = null
                    _audioCaptureAvailable.value = false
                    status(source, "STOPPED")
                }
            }
        }
    }

    private fun setupOverlay() {
        val bounds = displayBounds()
        val savedBattleHudLayout = battleHudLayoutStore.load(bounds.width(), bounds.height())
        val collapsedSizePx = (48f * resources.displayMetrics.density).roundToInt()
        val panelWidthPx = BattleHudOverlayGeometry.panelWidthPx(bounds.width())
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                // Keep Overdex's own field UI out of screen captures even if an
                // OEM compositor includes overlay windows in a projection.
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (bounds.width() - collapsedSizePx).coerceAtLeast(0)
            y = 350
        }
        freeOverlayX = params.x
        freeOverlayY = params.y
        overlayParams = params

        overlayView = ComposeView(this).apply {
            setContent {
                BattleOverlay(
                    panelWidthPx = panelWidthPx,
                    initialLayout = savedBattleHudLayout,
                    onDrag = { deltaX, deltaY -> moveOverlayBy(deltaX, deltaY) },
                    onDragFinished = ::snapOverlayToNearestEdge,
                    onBattleHudDrag = ::moveBattleHudBy,
                    onBattleHudDragFinished = ::saveBattleHudPosition,
                    onHalfPositionsChanged = ::saveBattleHudHalfPositions,
                    onResetBattleHudLayout = ::resetBattleHudLayout,
                    onLayoutStateChanged = ::updateOverlayLayoutState,
                )
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

    private fun moveOverlayBy(deltaX: Float, deltaY: Float) {
        if (overlayAnchoredToBattleHud) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        freeOverlayX = params.x
        freeOverlayY = params.y
        windowManager.updateViewLayout(view, params)
    }

    private fun snapOverlayToNearestEdge() {
        if (overlayAnchoredToBattleHud) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)
        params.x = if (params.x + viewWidth / 2 < bounds.width() / 2) 0 else (bounds.width() - viewWidth).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, (bounds.height() - viewHeight).coerceAtLeast(0))
        freeOverlayX = params.x
        freeOverlayY = params.y
        windowManager.updateViewLayout(view, params)
    }

    /** Moves the anchored battle panel exactly where the user places it during a test match. */
    private fun moveBattleHudBy(deltaX: Float, deltaY: Float) {
        if (!overlayAnchoredToBattleHud) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        val windowScreenOffsetY = overlayWindowScreenOffsetY(view, params)
        params.x = (params.x + deltaX.toInt()).coerceIn(
            0,
            (bounds.width() - view.width.coerceAtLeast(1)).coerceAtLeast(0),
        )
        params.y = (params.y + deltaY.toInt()).coerceIn(
            BattleHudOverlayGeometry.minimumPanelWindowTopPx(bounds.height(), windowScreenOffsetY),
            (bounds.height() - view.height.coerceAtLeast(1)).coerceAtLeast(0),
        )
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun saveBattleHudPosition() {
        if (!overlayAnchoredToBattleHud) return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        battleHudLayoutStore.saveWindow(bounds.width(), bounds.height(), params.x, params.y)
    }

    private fun saveBattleHudHalfPositions(
        topHalfOffsetX: Float,
        topHalfOffsetY: Float,
        bottomHalfOffsetX: Float,
        bottomHalfOffsetY: Float,
    ) {
        val bounds = displayBounds()
        battleHudLayoutStore.saveHalves(
            bounds.width(),
            bounds.height(),
            topHalfOffsetX,
            topHalfOffsetY,
            bottomHalfOffsetX,
            bottomHalfOffsetY,
        )
    }

    private fun resetBattleHudLayout() {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        battleHudLayoutStore.reset(bounds.width(), bounds.height())
        applyBattleHudWindowPosition(view, params, bounds, BattleHudLayout())
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun applyBattleHudWindowPosition(
        view: android.view.View,
        params: WindowManager.LayoutParams,
        bounds: android.graphics.Rect,
        layout: BattleHudLayout = battleHudLayoutStore.load(bounds.width(), bounds.height()),
    ) {
        val windowScreenOffsetY = overlayWindowScreenOffsetY(view, params)
        val minimumWindowTop = BattleHudOverlayGeometry.minimumPanelWindowTopPx(
            bounds.height(),
            windowScreenOffsetY,
        )
        val oldUntranslatedFloor = BattleHudOverlayGeometry.panelTopPx(bounds.height())
        val savedY = layout.windowY
        params.x = layout.windowX ?: BattleHudOverlayGeometry.panelLeftPx(bounds.width())
        params.y = when {
            // v2 positions at or above this floor were either saved with the old
            // Droidball/spacer geometry or stopped by the untranslated clamp.
            // Both should migrate to the first safe pixel below the GO badge.
            savedY == null -> minimumWindowTop
            savedY <= oldUntranslatedFloor -> minimumWindowTop
            else -> savedY.coerceAtLeast(minimumWindowTop)
        }
    }

    /**
     * WindowManager.LayoutParams.y and captured-screen Y are not always the same
     * coordinate on phones with status-bar or developer-overlay insets. Measure
     * that translation from the attached view instead of guessing its height.
     */
    private fun overlayWindowScreenOffsetY(
        view: android.view.View,
        params: WindowManager.LayoutParams,
    ): Int {
        if (!view.isAttachedToWindow) return 0
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1] - params.y
    }

    /**
     * Live battle presentation is attached to GO's opponent Team Info badge.
     * Other modes retain the user's movable, edge-snapping Droidball position.
     */
    private fun updateOverlayLayoutState(anchoredToBattleHud: Boolean, panelVisible: Boolean) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val wasAnchored = overlayAnchoredToBattleHud
        if (anchoredToBattleHud) {
            if (!wasAnchored) {
                freeOverlayX = params.x
                freeOverlayY = params.y
            }
            overlayAnchoredToBattleHud = true
            val bounds = displayBounds()
            applyBattleHudWindowPosition(view, params, bounds)
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
            // The first state callback can precede WindowManager's initial layout.
            // Reapply once attached so getLocationOnScreen supplies the real inset.
            view.post {
                if (overlayAnchoredToBattleHud && overlayView === view && view.isAttachedToWindow) {
                    val liveParams = overlayParams ?: return@post
                    applyBattleHudWindowPosition(view, liveParams, displayBounds())
                    windowManager.updateViewLayout(view, liveParams)
                }
            }
            return
        }

        overlayAnchoredToBattleHud = false
        if (wasAnchored) {
            params.x = freeOverlayX ?: params.x
            params.y = freeOverlayY ?: params.y
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
        }
        // Expansion changes the window width. Let Compose finish measuring it,
        // then keep the entire movable panel inside the display.
        if (panelVisible) view.post(::snapOverlayToNearestEdge)
    }

    private fun displayBounds(): android.graphics.Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.maximumWindowMetrics.bounds
        } else {
            android.graphics.Rect(
                0,
                0,
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels
            )
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
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] DroidballService.onDestroy()")
        if (activeService === this) activeService = null
        Log.d("DroidballService", "onDestroy: Releasing resources")
        markStopped()
        _signals.tryEmit(DroidballSignal.Stopped)
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        try {
            overlayView?.let { 
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            }
        } catch (e: Exception) {
            Log.e("DroidballService", "Error removing overlayView", e)
        } finally {
            overlayView = null
        }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e("DroidballService", "Error releasing virtualDisplay", e)
        } finally {
            virtualDisplay = null
        }

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("DroidballService", "Error closing imageReader", e)
        } finally {
            imageReader = null
        }

        captureHandler = null
        captureThread?.quitSafely()
        captureThread = null

        try {
            mediaProjection?.let {
                it.unregisterCallback(projectionCallback)
                it.stop()
            }
        } catch (e: Exception) {
            Log.e("DroidballService", "Error stopping mediaProjection", e)
        } finally {
            mediaProjection = null
        }

        _audioCaptureAvailable.value = false
        _audioInputStatus.value = _audioInputStatus.value.copy(state = "STOPPED")
        audioCaptureJob?.cancel()
        audioCaptureJob = null
        try { audioRecord?.stop() } catch (_: Exception) { }
        try { audioRecord?.release() } catch (_: Exception) { }
        audioRecord = null

        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e("DroidballService", "Error stopping device-motion capture", e)
        } finally {
            sensorManager = null
            linearAccelerationSensor = null
            motionPulseDetector.reset()
            _motionPulseCaptureAvailable.value = false
        }

        deliveryLoggingJob?.cancel()
        val subs = _frames.subscriptionCount.value
        Log.d("FRAME_DELIVERY", "Final Summary [Stop]: attempts=$publicationAttempts, emitSucceeded=$successEmitCount, rejected=$rejectedEmitCount, subscribers=$subs")

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

sealed class DroidballSignal {
    object Started : DroidballSignal()
    object Stopped : DroidballSignal()
    object FrameCaptured : DroidballSignal()
    data class Error(val message: String) : DroidballSignal()
    data class CountdownWitnessed(val value: String) : DroidballSignal()
    data object VsScreenWitnessed : DroidballSignal()
    /** A battle-entry announcement such as "GO, Pokémon!" was preserved. */
    data object BattleHudWitnessed : DroidballSignal()
    /** User explicitly opens the field HUD when visual recognition has not arrived yet. */
    data object OpenBattleHudRequested : DroidballSignal()
    data object ScanTeamSelectRequested : DroidballSignal()
    data object RestartObservationRequested : DroidballSignal()
    data object IgnoreCurrentScreenRequested : DroidballSignal()
    data object StopSessionRequested : DroidballSignal()
    data object ConfirmInferredPlayerTeamRequested : DroidballSignal()
    data object RejectInferredPlayerTeamRequested : DroidballSignal()
    data object BeginNextMatch : DroidballSignal()
    data class VisualCaptureGap(val durationNanos: Long) : DroidballSignal()
}
