package com.example.overdex.battle.observation

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.example.overdex.battle.audio.AudioCaptureCue
import com.example.overdex.battle.audio.BattleAudioCapturePlan
import com.example.overdex.battle.audio.BattleCryCueKind
import com.example.overdex.battle.audio.Pcm16CaptureFormat
import com.example.overdex.model.observation.CapturedAudioFrame
import com.example.overdex.model.observation.CapturedDeviceMotionPulse
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.ui.components.BattleOverlay
import com.example.overdex.ui.components.BattleHudOverlayGeometry
import com.example.overdex.ui.components.DroidballFieldGeometry
import com.example.overdex.ui.components.FieldBattleMenuGeometry
import com.example.overdex.presentation.DroidballJourney
import com.example.overdex.presentation.DroidballChoreography
import com.example.overdex.presentation.DroidballJourneyPhase
import com.example.overdex.presentation.DroidballJourneyState
import android.widget.FrameLayout
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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

/** The compact body's dock is independent of the temporary panel's width. */
internal fun droidballFreeWindowLeft(
    displayWidth: Int,
    windowWidth: Int,
    bodyWidth: Int,
    bodyDockLeft: Int,
    panelVisible: Boolean,
): Int = if (!panelVisible) {
    bodyDockLeft.coerceIn(0, (displayWidth - bodyWidth).coerceAtLeast(0))
} else if (bodyDockLeft + bodyWidth / 2 < displayWidth / 2) {
    0
} else {
    (displayWidth - windowWidth).coerceAtLeast(0)
}

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
        private val foregroundStartup = DroidballForegroundStartupGate()
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
        @Volatile private var activeService: DroidballService? = null
        val qteTypeIconSampling = MutableStateFlow<QteTypeIconSampling?>(null)
        @Volatile var qteCalibrationRegion: com.example.overdex.model.AnchorRegion? = null
        private val captureVisibility = LiveCaptureVisibilityGate()
        private val instrumentVisible = MutableStateFlow(true)

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        fun setInstrumentVisible(visible: Boolean) {
            captureVisibility.instrumentVisible(visible, System.nanoTime())
            instrumentVisible.value = visible
            if (visible) activeService?.takeIf { it.registrationMode }?.registrationController?.dismissField()
            if (visible) _frames.resetReplayCache()
        }

        private val _runtimeState = MutableStateFlow(DroidballRuntimeState.STOPPED)
        val runtimeState = _runtimeState.asStateFlow()

        fun isRunning(): Boolean = _runtimeState.value != DroidballRuntimeState.STOPPED

        internal fun handleNotificationControl(action: String?, sessionId: String?, matchId: String?) {
            val service = activeService ?: return
            if (!droidballNotificationTargetsCurrentSession(sessionId, service.notificationSessionId,
                    matchId, service.fieldViewModel.activeMatch.value?.matchId)) return
            when (action) {
                DROIDBALL_STOP_ACTION -> service.fieldViewModel.stopObservationFromNotification(matchId)
                DROIDBALL_NEXT_ACTION -> if (!service.registrationMode && matchId != null &&
                    runtimeState.value == DroidballRuntimeState.ACTIVE) {
                    _signals.tryEmit(DroidballSignal.BeginNextMatchRequested(matchId))
                }
            }
        }
        suspend fun captureRegistrationCry(): com.example.overdex.battle.audio.Pcm16Audio {
            val service = activeService?.takeIf { it.registrationMode } ?: error("Start a registration scan first.")
            check(service.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                "Allow audio recording for Overdex, then restart the scan."
            }
            val projection = service.mediaProjection ?: error("Screen sharing has stopped.")
            return com.example.overdex.data.registration.RegistrationCryCapture.record(service, projection)
        }
        
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

        private val _audioWindowFailures = MutableSharedFlow<com.example.overdex.battle.audio.CueCenteredPcmUnavailable>(
            extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val audioWindowFailures = _audioWindowFailures.asSharedFlow()

        private val _livePcm = MutableSharedFlow<com.example.overdex.battle.audio.LivePcmChunk>(
            replay = 0, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        /** Bounded, volatile samples for short sound-cue recognition; no full-match audio file. */
        val livePcm = _livePcm.asSharedFlow()

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

        fun start(context: Context, resultCode: Int, data: Intent, registration: Boolean = false) {
            if (!foregroundStartup.beginStart()) return
            _runtimeState.value = DroidballRuntimeState.STARTING
            val intent = Intent(context, DroidballService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("registration", registration)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (error: Throwable) {
                foregroundStartup.markStopped()
                _runtimeState.value = DroidballRuntimeState.STOPPED
                throw error
            }
        }

        fun stop(context: Context) {
            when (foregroundStartup.requestStop()) {
                // Android still needs its foreground acknowledgement. The
                // cancelled command will stop before creating any capture.
                DroidballForegroundStartupGate.StopAction.DEFER -> Unit
                DroidballForegroundStartupGate.StopAction.STOP_NOW ->
                    context.stopService(Intent(context, DroidballService::class.java))
                DroidballForegroundStartupGate.StopAction.NONE -> Unit
            }
        }

        /** Moves the visible field control without changing its observation state. */
        fun moveOverlayBy(deltaX: Float, deltaY: Float) {
            activeService?.moveOverlayBy(deltaX, deltaY)
        }

        /** Settles the field control against the nearer display edge. */
        fun snapOverlayToNearestEdge() {
            activeService?.snapOverlayToNearestEdge()
        }

        /** Request the sound at the visual source time, even when its testimony arrives late. */
        fun requestCueCenteredAudio(articleId: String, cueKind: BattleCryCueKind, observedAtMonotonicNanos: Long?) {
            activeService?.requestCueCenteredAudio(AudioCaptureCue(articleId, cueKind, observedAtMonotonicNanos))
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
    private var registrationMode = false
    private var registrationBoxes: com.example.overdex.ui.components.RegistrationAlignmentWindows? = null
    private var qteCalibrationWindow: com.example.overdex.ui.components.LiveQteCalibrationWindow? = null
    private val registrationPanelExpanded = androidx.compose.runtime.mutableStateOf(false)
    private val registrationController by lazy {
        androidx.lifecycle.ViewModelProvider(
            application as com.example.overdex.OverdexApplication,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[com.example.overdex.ui.PokedexViewModel::class.java].guidedRegistration
    }
    private val fieldViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(
            application as com.example.overdex.OverdexApplication,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[com.example.overdex.ui.PokedexViewModel::class.java]
    }
    @Volatile private var fieldEditorResumeAtNanos = 0L
    private var fieldEditorOriginalPosition: Pair<Int, Int>? = null
    private fun fieldEditorBlocksCapture(now: Long): Boolean = !registrationMode &&
        (fieldViewModel.fieldTeamEditor.draft.value != null || now < fieldEditorResumeAtNanos)
    private var overlayView: FrameLayout? = null
    private val badgeCalibration by lazy { com.example.overdex.CalibrationManager(this).load() }
    private val retainedOpponentBadge by lazy { RetainedOpponentBadge(this, windowManager) }
    @Volatile private var capturedOverlayRegion: com.example.overdex.model.AnchorRegion? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    // The renderer and native window share one physical canvas for this overlay.
    private var fieldBodyCanvasWidthPx = 1
    private var fieldWindowScreenOffsetY: Int? = null
    private var overlayAnchoredToBattleHud = false
    private var overlayPanelVisible = false
    private var overlayJourneyTravel = false
    private var overlayJourneyCompletionJob: kotlinx.coroutines.Job? = null
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
    // Retain eight seconds in RAM; only a 500 ms pre / 700 ms post cue window is saved.
    private var cueCenteredAudio = Pcm16CaptureFormat().cueCollector()
    private var firstFrameLogged = false
    private var lastPublishedFrameNanos: Long? = null

    private var publicationAttempts = 0L
    private var successEmitCount = 0L
    private var rejectedEmitCount = 0L
    private var deliveryLoggingJob: kotlinx.coroutines.Job? = null
    private var notificationStateJob: kotlinx.coroutines.Job? = null
    private var screenLockCheckJob: kotlinx.coroutines.Job? = null
    private var screenStateReceiverRegistered = false
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val keyguardManager by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val automaticStopController = DroidballAutomaticStopController { reason ->
        if (activeService === this) {
            Log.i("DROIDBALL_LIFECYCLE", "Automatically ending observation: ${reason.name}")
            // This owns the same archival/session teardown as the Stop control,
            // without claiming a user Stop action or inventing a battle result.
            fieldViewModel.stopObservationAutomatically(reason.description)
        }
    }
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT ->
                    refreshScreenLockCheck()
            }
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d("DroidballService", "MediaProjection stopped by system")
            // Newer Android versions can revoke projection on lock before our
            // screen broadcast arrives. End the whole session in either order.
            automaticStopController.onProjectionStopped(powerManager.isInteractive, keyguardManager.isKeyguardLocked)
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.d("ODX_CAPTURE_GEOMETRY", "onCapturedContentResize: captured-content dimensions: ${width}x${height}")
        }
    }

    private fun markStopped() {
        foregroundStartup.markStopped()
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
    private val notificationSessionId = java.util.UUID.randomUUID().toString()
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
        createDroidballNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] DroidballService.onStartCommand()")
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        registrationMode = intent?.getBooleanExtra("registration", false) == true

        if (resultCode == Activity.RESULT_OK && data != null) {
            // Fulfil Android's startup contract before expensive state access
            // or honoring a Stop queued while this command was still pending.
            val startingNotification = buildDroidballNotification(this, DROIDBALL_CHANNEL_ID,
                DroidballNotificationState.OBSERVING, registrationMode,
                sessionId = notificationSessionId, matchId = null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                    if (!registrationMode && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                startForeground(DROIDBALL_NOTIFICATION_ID, startingNotification, serviceTypes)
            } else {
                startForeground(DROIDBALL_NOTIFICATION_ID, startingNotification)
            }
            if (!foregroundStartup.foregroundPromoted()) {
                Log.i("DROIDBALL_LIFECYCLE", "Cancelled startup: foreground acknowledged; capture was not opened")
                stopSelf()
                return START_NOT_STICKY
            }
            observeNotificationState()
            runCatching {
                setupMediaProjection(resultCode, data)
                setupOverlay()
                if (!registrationMode) startAudioCapture()
                if (!registrationMode) runCatching { startMotionPulseCapture() }.onFailure { error ->
                    _motionPulseCaptureAvailable.value = false
                    Log.w("DEVICE_MOTION", "Unable to start optional motion-pulse capture", error)
                }
                _captureDiagnostics.value = CaptureDiagnostics(state = "READY", width = null, height = null, publicationNanoTime = null)
                _runtimeState.value = DroidballRuntimeState.ACTIVE
                if (!registrationMode) DroidballRuntimeMarker.markActive(this)
                _signals.tryEmit(DroidballSignal.Started)
                observeScreenLockState()
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

    private fun observeScreenLockState() {
        if (!screenStateReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(this, screenStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            screenStateReceiverRegistered = true
        }
        // Cover startup while already asleep; SCREEN_OFF is not a sticky broadcast.
        refreshScreenLockCheck()
    }

    private fun refreshScreenLockCheck() {
        screenLockCheckJob?.cancel()
        if (!screenStateReceiverRegistered) return
        screenLockCheckJob = serviceScope.launch {
            while (isActive) {
                // Re-read both values: queued broadcasts must not stop a phone
                // that has already woken up. Keyguard includes swipe-only locks.
                val recheck = automaticStopController.onDeviceState(
                    interactive = powerManager.isInteractive,
                    keyguardShowing = keyguardManager.isKeyguardLocked,
                )
                if (!recheck) break
                // Lock state can settle after SCREEN_OFF. No polling while awake,
                // and no wake lock: a sleeping device may defer the next check.
                delay(500L)
            }
        }
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
            var previousSuccessCount = 0L
            var previousLogNanos = System.nanoTime()
            while (true) {
                kotlinx.coroutines.delay(5000L)
                val now = System.nanoTime()
                val subs = _frames.subscriptionCount.value
                val elapsed = (now - previousLogNanos).coerceAtLeast(1L)
                val delivered = successEmitCount - previousSuccessCount
                val measuredFps = (delivered * 10_000_000_000L / elapsed).toFloat() / 10f
                Log.d(
                    "FRAME_DELIVERY",
                    "Cumulative Summary [5s]: attempts=$publicationAttempts, emitSucceeded=$successEmitCount, " +
                        "rejected=$rejectedEmitCount, subscribers=$subs, measuredFps=$measuredFps, " +
                        "targetFps=${LiveCaptureSamplingPolicy.TARGET_FRAMES_PER_SECOND}"
                )
                previousSuccessCount = successEmitCount
                previousLogNanos = now
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
                    if (!captureVisibility.permits(receivedAtNanos) || fieldEditorBlocksCapture(receivedAtNanos)) return@setOnImageAvailableListener
                    if (registrationMode && !registrationController.wantsFrames) return@setOnImageAvailableListener
                    if (registrationMode && lastPublishedFrameNanos?.let { receivedAtNanos - it < 500_000_000L } == true) {
                        return@setOnImageAvailableListener
                    }
                    if (!LiveCaptureSamplingPolicy.shouldPublish(lastPublishedFrameNanos, receivedAtNanos)) {
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

                    // The activity can become visible while the pixels are copied.
                    if (!captureVisibility.permits(receivedAtNanos) || fieldEditorBlocksCapture(receivedAtNanos)) {
                        bitmap.recycle()
                        return@setOnImageAvailableListener
                    }
                    if (!firstFrameLogged) {
                        Log.d("ODX_CAPTURE_GEOMETRY", "└── Final Published Bitmap: ${bitmap.width}x${bitmap.height}")
                        firstFrameLogged = true
                    }

                    val capturedAtWallTimeMillis = System.currentTimeMillis()
                    // VS/countdown or corroborated battle witnesses must establish
                    // the surface first. Overworld colors cannot open the HUD or
                    // become a retained badge, even after a manual HUD request.
                    val badgeSession = if (registrationMode) null else fieldViewModel.droidballSession.value
                    val battleSurfaceEstablished = badgeSession?.battleSurfaceEstablished?.value == true &&
                        !badgeSession.hasCompletedMatch
                    val badgeScope = badgeSession?.let {
                        RetainedBadgeScope(it.match.matchId, it.match.observationAttemptStartedAtNanos)
                    }
                    val badgeExclusionBefore = retainedOpponentBadge.exclusion(receivedAtNanos)
                    val nativeFrame = CapturedVisualFrame(bitmap, capturedAtWallTimeMillis, receivedAtNanos,
                        overlayRegion = capturedOverlayRegion, retainedBadgeRegion = badgeExclusionBefore)
                    val playerBadgeRegion = if (!battleSurfaceEstablished) badgeCalibration.playerTeamInfoRegion else
                        TeamBadgeCropLocator.locate(BattleRegionId.PLAYER_TEAM_INFO, badgeCalibration, nativeFrame)
                    val opponentBadgeRegion = if (!battleSurfaceEstablished || badgeExclusionBefore != null)
                        badgeCalibration.opponentTeamInfoRegion else
                        TeamBadgeCropLocator.locate(BattleRegionId.OPPONENT_TEAM_INFO, badgeCalibration, nativeFrame)
                    val playerBadgePresent = battleSurfaceEstablished &&
                        TeamBadgeCropLocator.nativeBadgePresent(nativeFrame, playerBadgeRegion)
                    val opponentBadgePresent = battleSurfaceEstablished && badgeExclusionBefore == null &&
                        TeamBadgeCropLocator.nativeBadgePresent(nativeFrame, opponentBadgeRegion)
                    if (!registrationMode && playerBadgePresent && opponentBadgePresent) {
                        // Returning through Recents has no explicit GO launch.
                        // This frame's two native badges can restore field
                        // ownership without waiting for another semantic event.
                        val waitingJourney = DroidballJourney.state.value
                        if (!instrumentVisible.value && waitingJourney.phase == DroidballJourneyPhase.TRANSIT &&
                            receivedAtNanos >= waitingJourney.startedAtNanos) {
                            serviceScope.launch(Dispatchers.Main) {
                                // The user may have returned home while this
                                // frame was processed. Recheck on the UI thread;
                                // an old positive must never undock the shell.
                                if (activeService === this@DroidballService && isRunning() &&
                                    !instrumentVisible.value && DroidballJourney.state.value == waitingJourney) {
                                    DroidballJourney.enterField()
                                }
                            }
                        }
                    }
                    if (!registrationMode && !DroidballOverlayPresentation.battleBadgeSeen.value &&
                        playerBadgePresent && opponentBadgePresent) {
                        DroidballOverlayPresentation.recordBattleBadgePresent()
                        DroidballOverlayPresentation.showBattleHud()
                        emitSignal(DroidballSignal.BattleHudWitnessed)
                    }
                    if (!registrationMode) {
                        retainedOpponentBadge.update(bitmap, receivedAtNanos, playerBadgePresent, opponentBadgePresent,
                            opponentBadgeRegion, width, height, badgeScope,
                            battleSurfaceEstablished &&
                                DroidballOverlayPresentation.panelAllowed.value &&
                                DroidballOverlayPresentation.expanded.value &&
                                DroidballJourney.state.value.phase == DroidballJourneyPhase.FIELD &&
                                DroidballOverlayPresentation.mode.value == DroidballOverlayMode.BATTLE_HUD &&
                                DroidballOverlayPresentation.battleBadgeSeen.value,
                            capturedOverlayRegion, DroidballOverlayPresentation.opponentBadgeContent.value)
                    }
                    val capturedAtMonotonicTimeNanos = receivedAtNanos
                    lastPublishedFrameNanos?.let { previous ->
                        val gap = capturedAtMonotonicTimeNanos - previous
                        if (gap > 750_000_000L) _signals.tryEmit(DroidballSignal.VisualCaptureGap(gap))
                    }
                    lastPublishedFrameNanos = capturedAtMonotonicTimeNanos
                    val frame = CapturedVisualFrame(
                        bitmap = bitmap,
                        capturedAtWallTimeMillis = capturedAtWallTimeMillis,
                        capturedAtMonotonicTimeNanos = capturedAtMonotonicTimeNanos,
                        displayWidth = width,
                        displayHeight = height,
                        overlayRegion = capturedOverlayRegion,
                        retainedBadgeRegion = badgeExclusionBefore ?: retainedOpponentBadge.exclusion(receivedAtNanos),
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
        if (audioRecord == null) return // AUDIO_INPUT_STATUS already reports unavailable input.
        synchronized(audioCollectorLock) { cueCenteredAudio.cue(cue) }
            ?.let { _audioWindowFailures.tryEmit(it) }
    }

    /** Prefer game-only playback. A silent permitted stream stays explicitly silent. */
    private fun startAudioCapture() {
        var captureFormat: Pcm16CaptureFormat? = null
        fun status(source: String, state: String) {
            _audioInputStatus.value = com.example.overdex.battle.custody.AudioInputStatus(
                source, state, captureFormat?.sampleRateHz, captureFormat?.channelCount)
            Log.i("ODX_AUDIO_INPUT", "source=$source state=$state " +
                "rate=${captureFormat?.sampleRateHz} channels=${captureFormat?.channelCount}")
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            _audioCaptureAvailable.value = false
            status("NONE", "PERMISSION_UNAVAILABLE")
            return
        }
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val started = BattleAudioCapturePlan.start { request ->
            var candidate: AudioRecord? = null
            try {
                val format = request.format
                val channelConfig = if (format.channelCount == 2) AudioFormat.CHANNEL_IN_STEREO
                    else AudioFormat.CHANNEL_IN_MONO
                val minimumBuffer = AudioRecord.getMinBufferSize(format.sampleRateHz, channelConfig, encoding)
                check(minimumBuffer > 0) { "Unsupported PCM format: $format" }
                val bufferSize = maxOf(minimumBuffer, format.bytesForMillis(200))
                candidate = if (request.source == "PLAYBACK_POKEMON_GO") {
                    val uid = packageManager.getApplicationInfo("com.nianticlabs.pokemongo", 0).uid
                    val capture = AudioPlaybackCaptureConfiguration.Builder(requireNotNull(mediaProjection))
                        .addMatchingUid(uid)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build()
                    AudioRecord.Builder()
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(format.sampleRateHz)
                            .setChannelMask(channelConfig).setEncoding(encoding).build())
                        .setBufferSizeInBytes(bufferSize)
                        .setAudioPlaybackCaptureConfig(capture)
                        .build()
                } else {
                    AudioRecord(MediaRecorder.AudioSource.MIC, format.sampleRateHz, channelConfig, encoding, bufferSize)
                }
                check(candidate.state == AudioRecord.STATE_INITIALIZED)
                check(candidate.channelCount == format.channelCount && candidate.sampleRate == format.sampleRateHz &&
                    candidate.audioFormat == encoding) { "Recorder format differs from requested $format" }
                candidate.startRecording()
                check(candidate.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                candidate
            } catch (error: Exception) {
                try { candidate?.release() } catch (_: Exception) { }
                Log.w("ODX_AUDIO_INPUT", "Could not start ${request.source} ${request.format}", error)
                null
            }
        }
        if (started == null) {
            _audioCaptureAvailable.value = false
            status("NONE", "START_FAILED")
            return
        }
        val (request, record) = started
        val source = request.source
        // Use the verified recorder format for every byte count, timestamp duration and WAV header.
        val format = Pcm16CaptureFormat(record.sampleRate, record.channelCount)
        captureFormat = format
        synchronized(audioCollectorLock) { cueCenteredAudio = format.cueCollector() }
        audioRecord = record
        _audioCaptureAvailable.value = true
        status(source, "RUNNING_AWAITING_SIGNAL")
        audioCaptureJob = serviceScope.launch(Dispatchers.IO) {
            // A short read keeps the rolling buffer responsive to visual cues.
            val readBuffer = ByteArray(format.bytesForMillis(20))
            var lastSignalAt = System.nanoTime()
            var lastState = "RUNNING_AWAITING_SIGNAL"
            var pcmSequence = 0L
            try {
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    val count = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) { status(source, "READ_FAILED"); break }
                    if (count % format.bytesPerFrame != 0) { status(source, "UNALIGNED_READ"); break }
                    val peak = com.example.overdex.battle.audio.PcmSignalLevel.peak(readBuffer, count)
                    val now = System.nanoTime()
                    val nowWallMillis = System.currentTimeMillis()
                    if (_livePcm.subscriptionCount.value > 0) {
                        // Readers make their own mono projection; keep the source channels intact.
                        _livePcm.tryEmit(com.example.overdex.battle.audio.LivePcmChunk(
                            format.decode(readBuffer, count),
                            pcmSequence++, now, nowWallMillis, source
                        ))
                    }
                    if (peak >= 0.001f) lastSignalAt = now
                    val state = when {
                        peak >= 0.001f -> "SIGNAL_PRESENT"
                        now - lastSignalAt >= 2_000_000_000L -> "QUIET_OR_UNAVAILABLE"
                        else -> lastState
                    }
                    if (state != lastState) { status(source, state); lastState = state }
                    val completed = synchronized(audioCollectorLock) {
                        cueCenteredAudio.ingest(readBuffer.copyOf(count), now, nowWallMillis)
                    }
                    completed.forEach { result ->
                        if (result is com.example.overdex.battle.audio.CueCenteredPcmUnavailable) {
                            _audioWindowFailures.tryEmit(result)
                            return@forEach
                        }
                        val capture = result as com.example.overdex.battle.audio.CueCenteredPcmCapture
                        _audioFrames.tryEmit(CapturedAudioFrame(
                            pcm16le = capture.pcm16le,
                            sampleRateHz = format.sampleRateHz,
                            channelCount = format.channelCount,
                            capturedAtWallTimeMillis = capture.startedAtWallTimeMillis,
                            capturedAtMonotonicTimeNanos = capture.startedAtMonotonicNanos,
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
        fieldWindowScreenOffsetY = null
        val bounds = displayBounds()
        val savedBattleHudLayout = battleHudLayoutStore.load(bounds.width(), bounds.height())
        val bodyCanvasWidthPx = DroidballFieldGeometry.bodyCanvasWidthPx(bounds.width())
        fieldBodyCanvasWidthPx = bodyCanvasWidthPx
        val panelWidthPx = BattleHudOverlayGeometry.panelWidthPx(bounds.width())
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            droidballOverlayWindowFlags(registrationMode),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (registrationMode) (bounds.width() - (184 * resources.displayMetrics.density).roundToInt()).coerceAtLeast(0)
                else DroidballFieldGeometry.dockedWindowLeftPx(bounds.width(), bodyCanvasWidthPx)
            y = 350
        }
        freeOverlayX = params.x
        freeOverlayY = params.y
        overlayParams = params

        val overlayContent = ComposeView(this).apply {
            setContent {
                if (registrationMode) {
                    com.example.overdex.ui.components.RegistrationScanOverlay(
                        controller = registrationController,
                        expanded = registrationPanelExpanded.value,
                        onExpandedChange = { registrationPanelExpanded.value = it },
                        onDrag = ::moveOverlayBy,
                        onReview = {
                            startActivity(Intent(this@DroidballService, com.example.overdex.MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra("open_guided_registration", true))
                        },
                    )
                } else {
                    val draft by fieldViewModel.fieldTeamEditor.draft.collectAsState()
                    val savedTeams by fieldViewModel.selectableBattleTeams.collectAsState()
                    val journey by DroidballJourney.state.collectAsState()
                    val traveling = journey.phase == DroidballJourneyPhase.ARRIVING || journey.phase == DroidballJourneyPhase.RETURNING
                    LaunchedEffect(draft != null) { setFieldEditorWindow(draft != null) }
                    if (draft != null && !traveling) com.example.overdex.ui.components.FieldTeamEditorOverlay(
                        editor = fieldViewModel.fieldTeamEditor,
                        savedTeams = savedTeams,
                        width = (FieldBattleMenuGeometry.widthPx(bounds.width()) / resources.displayMetrics.density).dp,
                        onRescan = { emitSignal(DroidballSignal.ScanTeamSelectRequested) },
                    ) else BattleOverlay(
                    bodyCanvasWidthPx = bodyCanvasWidthPx,
                    panelWidthPx = panelWidthPx,
                    fieldMenuWidthPx = FieldBattleMenuGeometry.widthPx(bounds.width()),
                    fieldMenuHeightPx = FieldBattleMenuGeometry.heightPx(bounds.height()),
                    initialLayout = savedBattleHudLayout,
                    onDrag = { deltaX, deltaY ->
                        if (overlayAnchoredToBattleHud) moveBattleHudBy(deltaX, deltaY)
                        else moveOverlayBy(deltaX, deltaY)
                    },
                    onDragFinished = {
                        if (overlayAnchoredToBattleHud) saveBattleHudPosition()
                        else snapOverlayToNearestEdge()
                    },
                    onHalfPositionsChanged = ::saveBattleHudHalfPositions,
                    onLayoutStateChanged = ::updateOverlayLayoutState,
                    onJourneyOffsetChanged = ::moveJourneyWindow,
                    )
                }
            }
        }
        overlayView = FrameLayout(this).apply {
            addView(overlayContent, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        
        // Essential for Compose in WindowManager
        overlayView!!.setViewTreeLifecycleOwner(this)
        overlayView!!.setViewTreeViewModelStoreOwner(this)
        overlayView!!.setViewTreeSavedStateRegistryOwner(this)
        overlayView!!.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (fieldWindowScreenOffsetY == null) {
                val location = IntArray(2)
                overlayView!!.getLocationOnScreen(location)
                fieldWindowScreenOffsetY = location[1] - params.y
            }
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                alignFreeOverlayWindow()
            }
        }

        // Read geometry on the UI thread. Capture workers retain this immutable
        // screen-space footprint alongside each frame, without repainting it.
        overlayView!!.viewTreeObserver.addOnPreDrawListener {
            val view = overlayView
            // Capture the attached origin once, before later moves enqueue new
            // LayoutParams. Comparing an old frame with a pending params.y
            // creates a false inset and feeds the movement back into itself.
            if (view != null && fieldWindowScreenOffsetY == null && view.isAttachedToWindow) {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                fieldWindowScreenOffsetY = location[1] - params.y
            }
            capturedOverlayRegion = if (view != null && view.isShown && !registrationMode &&
                (!overlayAnchoredToBattleHud || DroidballOverlayPresentation.battleBadgeSeen.value)) {
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                com.example.overdex.model.AnchorRegion(location[0].toFloat() / bounds.width(),
                    location[1].toFloat() / bounds.height(), view.width.toFloat() / bounds.width(),
                    view.height.toFloat() / bounds.height())
            } else {
                null
            }
            true
        }
        
        // The native window must share the same owner as the shell body. Do
        // not expose a second, static ball while GO is opening.
        if (!registrationMode) overlayView?.visibility = android.view.View.INVISIBLE
        windowManager.addView(overlayView, params)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        serviceScope.launch(Dispatchers.Main) {
            instrumentVisible.collect { visible -> qteCalibrationWindow?.setVisible(!visible) }
        }
        if (!registrationMode) serviceScope.launch(Dispatchers.Main) {
            combine(instrumentVisible, DroidballJourney.state) { visible, journey ->
                visible to journey
            }.collect { (visible, journey) ->
                if (journey.phase == DroidballJourneyPhase.RETURNING && fieldViewModel.fieldTeamEditor.draft.value != null) {
                    fieldViewModel.fieldTeamEditor.close()
                    setFieldEditorWindow(false)
                }
                updateJourneyWindow(journey)
                val showField = journey.fieldOwnsBody &&
                    (!visible || journey.phase == DroidballJourneyPhase.RETURNING)
                overlayView?.visibility = if (showField) {
                    android.view.View.VISIBLE
                } else android.view.View.INVISIBLE
                if (!showField) {
                    capturedOverlayRegion = null
                    if (journey.fieldOwnsBody) com.example.overdex.media.DroidballJourneySounds.hidden(journey)
                }
                // Completion belongs to the service's journey, not to a
                // visible Compose frame (which Android may pause off screen).
                overlayJourneyCompletionJob?.cancel()
                if (journey.phase == DroidballJourneyPhase.ARRIVING || journey.phase == DroidballJourneyPhase.RETURNING) {
                    overlayJourneyCompletionJob = launch {
                        val elapsed = ((System.nanoTime() - journey.startedAtNanos).coerceAtLeast(0) / 1_000_000L)
                        delay((DroidballChoreography.durationMillis(journey.phase) - elapsed).coerceAtLeast(0))
                        if (journey.phase == DroidballJourneyPhase.ARRIVING) DroidballJourney.arrivalFinished(journey.generation)
                        else DroidballJourney.fieldExitFinished(journey.generation)
                    }
                }
            }
        }
        if (!registrationMode) serviceScope.launch(Dispatchers.Main) {
            DroidballOverlayPresentation.opponentBadgeContent.collect(retainedOpponentBadge::contentChanged)
        }
        if (!registrationMode) serviceScope.launch(Dispatchers.Main) {
            combine(instrumentVisible, DroidballOverlayPresentation.mode, DroidballOverlayPresentation.battleBadgeSeen,
                combine(DroidballOverlayPresentation.panelAllowed, DroidballOverlayPresentation.expanded) { allowed, expanded -> allowed && expanded },
                DroidballJourney.state) {
                visible, mode, seen, panelVisible, journey -> !visible && panelVisible && seen &&
                    journey.phase == DroidballJourneyPhase.FIELD && mode == DroidballOverlayMode.BATTLE_HUD
            }.distinctUntilChanged().collect { allowed -> if (!allowed) retainedOpponentBadge.reset() }
        }
        if (registrationMode) {
            registrationBoxes = com.example.overdex.ui.components.RegistrationAlignmentWindows(
                this, windowManager, this, this, this, registrationController.alignment, registrationController::readField)
            val screen = displayBounds()
            registrationController.alignment.begin(screen.width(), screen.height())
            serviceScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.flow.combine(registrationController.alignment.state, instrumentVisible, registrationController.fieldReview) { state, visible, review ->
                    Triple(state, visible, review != null)
                }.collect { (state, visible, reviewing) ->
                    // Guide windows must not cover or intercept the panel's controls.
                    val addedGuides = registrationBoxes?.render(if (visible || reviewing) state.copy(hiddenForRead = true) else state) == true
                    val panel = overlayView
                    val panelParams = overlayParams
                    if (addedGuides && panel != null && panelParams != null && panel.isAttachedToWindow) {
                        // WindowManager stacks new windows on top. Reattach above the guides;
                        // recreate the window composition and retain expansion in service state.
                        windowManager.removeViewImmediate(panel)
                        windowManager.addView(panel, panelParams)
                    }
                    panel?.visibility = if (visible || state.hiddenForRead) android.view.View.INVISIBLE else android.view.View.VISIBLE
                    if (!visible && state.hiddenForRead) registrationController.onGuidesHidden()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun openQteCalibration() {
        if (registrationMode || qteCalibrationWindow != null) return
        val bounds = displayBounds()
        val calibration = com.example.overdex.CalibrationManager(this)
        val window = com.example.overdex.ui.components.LiveQteCalibrationWindow(
            this, windowManager, this, this, this, serviceScope,
            bounds.width(), bounds.height(), calibration.load().qteTypeIconsRegion,
            saveRegion = { region ->
                // Load at save time: a live adjustment owns only this box, never
                // a stale copy of species, HP, shields or another user's edits.
                calibration.save(calibration.load().copy(qteTypeIconsRegion = region))
            },
            onClosed = { qteCalibrationWindow = null; qteCalibrationRegion = null },
            sampleRegion = { qteTypeIconSampling.value?.sample(it) == true },
            samplingStatus = qteTypeIconSampling.flatMapLatest { sampler ->
                sampler?.status ?: kotlinx.coroutines.flow.flowOf(QteIconSamplingStatus())
            },
            onRegionChanged = { qteCalibrationRegion = it },
        )
        qteCalibrationWindow = window
        try { window.show(); window.setVisible(!instrumentVisible.value) }
        catch (error: Exception) {
            window.close()
            Log.e("QTE_CALIBRATION", "Unable to open calibration guide", error)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun setFieldEditorWindow(open: Boolean) {
        if (open) retainedOpponentBadge.reset()
        val view = overlayView ?: return
        val params = overlayParams ?: return
        // Read the actual attached translation before changing params. Until
        // WindowManager lays out the restored window, its screen location still
        // belongs to the editor and cannot be compared with the new params.y.
        val screenOffsetBefore = overlayWindowScreenOffsetY(view, params)
        fieldEditorResumeAtNanos = System.nanoTime() + 250_000_000L
        _frames.resetReplayCache()
        if (open) {
            // Let Android screenshots include the editor. fieldEditorBlocksCapture
            // pauses our visual readers, so its text cannot become game evidence.
            if (fieldEditorOriginalPosition == null) fieldEditorOriginalPosition = params.x to params.y
            // Keep GO's start button behind the editor while visual capture is
            // paused. USE THIS TEAM or CLOSE returns touch input to the game.
            params.flags = params.flags and
                (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL).inv()
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            params.width = FieldBattleMenuGeometry.widthPx(displayBounds().width())
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = FieldBattleMenuGeometry.leftPx(displayBounds().width())
            params.y = (displayBounds().height() * .12f).roundToInt()
        } else {
            fieldEditorOriginalPosition?.let { (x, y) -> params.x = x; params.y = y }
            fieldEditorOriginalPosition = null
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
            overlayPanelVisible = DroidballOverlayPresentation.panelAllowed.value && DroidballOverlayPresentation.expanded.value
            overlayAnchoredToBattleHud = overlayPanelVisible &&
                battleOverlayDisplayMode(DroidballOverlayPresentation.mode.value, DroidballOverlayPresentation.battleBadgeSeen.value) in
                    setOf(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayMode.BATTLE_LIVE)
            val bounds = displayBounds()
            if (overlayAnchoredToBattleHud) {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                applyBattleHudWindowPosition(view, params, bounds, windowScreenOffsetY = screenOffsetBefore)
            } else if (overlayPanelVisible) {
                params.width = FieldBattleMenuGeometry.widthPx(bounds.width())
                params.height = fieldBodyCanvasWidthPx + FieldBattleMenuGeometry.heightPx(bounds.height())
                params.x = FieldBattleMenuGeometry.leftPx(bounds.width())
                params.y = FieldBattleMenuGeometry.windowTopPx(bounds.height(), fieldBodyCanvasWidthPx, screenOffsetBefore)
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
        }
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun moveOverlayBy(deltaX: Float, deltaY: Float) {
        if (overlayAnchoredToBattleHud || overlayJourneyTravel) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        params.x += deltaX.toInt()
        params.y += deltaY.toInt()
        // The expanded panel centers the body above itself. Keep the body
        // location, rather than treating the panel's left edge as a new dock.
        val bodyWidth = fieldBodyCanvasWidthPx
        freeOverlayX = params.x + if (!registrationMode && overlayPanelVisible) (view.width - bodyWidth).coerceAtLeast(0) / 2 else 0
        freeOverlayY = params.y
        windowManager.updateViewLayout(view, params)
    }

    private fun snapOverlayToNearestEdge() {
        if (overlayAnchoredToBattleHud || overlayJourneyTravel) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)
        val rightDock = params.x + viewWidth / 2 >= bounds.width() / 2
        params.y = params.y.coerceIn(0, (bounds.height() - viewHeight).coerceAtLeast(0))
        val bodyWidth = fieldBodyCanvasWidthPx
        if (registrationMode) {
            params.x = if (rightDock) (bounds.width() - viewWidth).coerceAtLeast(0) else 0
            freeOverlayX = params.x
        } else {
            val bodyDock = DroidballFieldGeometry.dockedWindowLeftPx(bounds.width(), bodyWidth, rightDock)
            freeOverlayX = bodyDock
            params.x = droidballFreeWindowLeft(bounds.width(), viewWidth, bodyWidth, bodyDock, overlayPanelVisible)
        }
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
            BattleHudOverlayGeometry.minimumEditableWindowTopPx(windowScreenOffsetY),
            (bounds.height() - view.height.coerceAtLeast(1)).coerceAtLeast(0),
        )
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun saveBattleHudPosition() {
        if (!overlayAnchoredToBattleHud) return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        // Keep persisted Y as the flat badge seam used by existing profiles.
        battleHudLayoutStore.saveWindow(bounds.width(), bounds.height(), params.x,
            BattleHudOverlayGeometry.savedSeamForWindowTop(params.y, bounds.width()))
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
        windowScreenOffsetY: Int = overlayWindowScreenOffsetY(view, params),
    ) {
        val attachedWindowTop = BattleHudOverlayGeometry.attachedPanelWindowTopPx(
            bounds.height(),
            windowScreenOffsetY,
        )
        val minimumWindowTop = BattleHudOverlayGeometry.minimumEditableWindowTopPx(
            windowScreenOffsetY,
        )
        val maximumWindowTop = (bounds.height() - view.height.coerceAtLeast(1))
            .coerceAtLeast(minimumWindowTop)
        val savedY = layout.windowY
        params.x = layout.windowX ?: BattleHudOverlayGeometry.panelLeftPx(bounds.width())
        params.y = BattleHudOverlayGeometry.windowTopForSavedSeam(savedY ?: attachedWindowTop, bounds.width())
            .coerceIn(minimumWindowTop, maximumWindowTop)
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
        fieldWindowScreenOffsetY?.let { return it }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[1] - params.y
    }

    /**
     * Live battle presentation is attached to GO's opponent Team Info badge.
     * The open field menu uses its party-card reference; the collapsed body
     * retains the user's movable, edge-snapping position.
     */
    private fun updateOverlayLayoutState(anchoredToBattleHud: Boolean, panelVisible: Boolean) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        // Arrival owns only a compact body window, not the saved HUD position.
        if (!registrationMode && DroidballJourney.state.value.phase in setOf(
                DroidballJourneyPhase.ARRIVING, DroidballJourneyPhase.RETURNING)) return
        overlayPanelVisible = panelVisible
        val wasAnchored = overlayAnchoredToBattleHud
        if (anchoredToBattleHud) {
            if (!wasAnchored) {
                if (freeOverlayX == null) freeOverlayX = params.x
                if (freeOverlayY == null) freeOverlayY = params.y
            }
            overlayAnchoredToBattleHud = true
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            val bounds = displayBounds()
            applyBattleHudWindowPosition(view, params, bounds)
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
            // The first state callback can precede WindowManager's initial layout.
            // Reapply once attached so getLocationOnScreen supplies the real inset.
            view.post {
                if (overlayAnchoredToBattleHud && !overlayJourneyTravel &&
                    fieldViewModel.fieldTeamEditor.draft.value == null &&
                    overlayView === view && view.isAttachedToWindow) {
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
        // Menu expansion must not replace the compact dock with the card's
        // origin. Reconcile again after actual native measurement.
        alignFreeOverlayWindow()
        view.post(::alignFreeOverlayWindow)
    }

    private fun alignFreeOverlayWindow() {
        if (registrationMode || overlayAnchoredToBattleHud || overlayJourneyTravel ||
            DroidballJourney.state.value.phase != DroidballJourneyPhase.FIELD ||
            fieldViewModel.fieldTeamEditor.draft.value != null) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bounds = displayBounds()
        val bodyWidth = fieldBodyCanvasWidthPx
        val width = if (overlayPanelVisible) FieldBattleMenuGeometry.widthPx(bounds.width())
            else WindowManager.LayoutParams.WRAP_CONTENT
        val height = if (overlayPanelVisible) bodyWidth + FieldBattleMenuGeometry.heightPx(bounds.height())
            else WindowManager.LayoutParams.WRAP_CONTENT
        // The wide menu is fixed to the native party-card reference. Keep the
        // collapsed body's edge dock separate so opening/closing never saves a
        // new body position or changes the user's battle HUD calibration.
        val x = if (overlayPanelVisible) FieldBattleMenuGeometry.leftPx(bounds.width())
            else freeOverlayX ?: params.x
        val y = if (overlayPanelVisible) FieldBattleMenuGeometry.windowTopPx(
            bounds.height(), bodyWidth, overlayWindowScreenOffsetY(view, params))
            else (freeOverlayY ?: params.y).coerceIn(0, (bounds.height() - view.height.coerceAtLeast(1)).coerceAtLeast(0))
        if (params.x != x || params.y != y || params.width != width || params.height != height) {
            params.width = width
            params.height = height
            params.x = x
            params.y = y
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
        }
    }

    /**
     * A compact native window follows the ball instead of laying a broad,
     * transparent touch-catching rectangle over the game. Temporary movement
     * never writes the user's HUD calibration or edge position.
     */
    private fun updateJourneyWindow(journey: DroidballJourneyState) {
        if (registrationMode) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val traveling = journey.phase == DroidballJourneyPhase.ARRIVING || journey.phase == DroidballJourneyPhase.RETURNING
        val bodyPixels = fieldBodyCanvasWidthPx
        val travelPadding = DroidballFieldGeometry.travelPaddingPx(bodyPixels)
        val travelWindowPixels = bodyPixels + travelPadding * 2
        val bounds = displayBounds()
        if (traveling) {
            overlayJourneyTravel = true
            overlayAnchoredToBattleHud = false
            params.width = travelWindowPixels
            params.height = travelWindowPixels
            params.flags = droidballOverlayWindowFlags(false) or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            val elapsed = ((System.nanoTime() - journey.startedAtNanos).coerceAtLeast(0) / 1_000_000L)
            params.x = DroidballFieldGeometry.windowLeftPx(bounds.width(), bodyPixels,
                DroidballChoreography.pose(journey.phase, elapsed).xBodies, travelPadding)
            params.y = ((freeOverlayY ?: 350) - travelPadding).coerceIn(0, (bounds.height() - travelWindowPixels).coerceAtLeast(0))
        } else if (overlayJourneyTravel) {
            overlayJourneyTravel = false
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS.inv()
            params.x = DroidballFieldGeometry.windowLeftPx(bounds.width(), bodyPixels, 0f)
            params.y = freeOverlayY ?: 350
            freeOverlayX = params.x
            // Reconcile against the usual battle seam only after the travel
            // window has released ownership; this does not persist a layout.
            if (journey.phase == DroidballJourneyPhase.FIELD) view.post {
                if (DroidballJourney.state.value != journey || overlayView !== view || !view.isAttachedToWindow ||
                    fieldViewModel.fieldTeamEditor.draft.value != null) return@post
                val panelAllowed = DroidballOverlayPresentation.panelAllowed.value
                val mode = battleOverlayDisplayMode(DroidballOverlayPresentation.mode.value,
                    DroidballOverlayPresentation.battleBadgeSeen.value)
                val panelVisible = panelAllowed && DroidballOverlayPresentation.expanded.value
                val anchored = panelVisible && mode in setOf(DroidballOverlayMode.BATTLE_HUD, DroidballOverlayMode.BATTLE_LIVE)
                updateOverlayLayoutState(anchored, panelVisible)
            }
        } else return
        if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
    }

    private fun moveJourneyWindow(bodyWidths: Float) {
        if (registrationMode || !overlayJourneyTravel) return
        val view = overlayView ?: return
        val params = overlayParams ?: return
        val bodyPixels = fieldBodyCanvasWidthPx
        val travelPadding = DroidballFieldGeometry.travelPaddingPx(bodyPixels)
        val nextX = DroidballFieldGeometry.windowLeftPx(displayBounds().width(), bodyPixels, bodyWidths, travelPadding)
        if (params.x != nextX) {
            params.x = nextX
            if (view.isAttachedToWindow) windowManager.updateViewLayout(view, params)
        }
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

    private fun createNotification(state: DroidballNotificationState = DroidballNotificationState.IDLE): Notification =
        buildDroidballNotification(this, DROIDBALL_CHANNEL_ID, state, registrationMode,
            sessionId = notificationSessionId, matchId = fieldViewModel.activeMatch.value?.matchId)

    private fun observeNotificationState() {
        notificationStateJob?.cancel()
        notificationStateJob = serviceScope.launch {
            combine(runtimeState, instrumentVisible, DroidballOverlayPresentation.mode,
                fieldViewModel.activeMatch) { runtime, visible, mode, match ->
                droidballNotificationState(runtime, visible, mode, registrationMode) to match?.matchId
            }.distinctUntilChanged().collect { (state, _) ->
                // Never recreate a foreground notification after capture has stopped.
                if (_runtimeState.value != DroidballRuntimeState.STOPPED) {
                    getSystemService(NotificationManager::class.java).notify(DROIDBALL_NOTIFICATION_ID, createNotification(state))
                }
            }
        }
    }

    override fun onDestroy() {
        overlayJourneyCompletionJob?.cancel()
        overlayJourneyCompletionJob = null
        automaticStopController.close()
        screenLockCheckJob?.cancel()
        screenLockCheckJob = null
        if (screenStateReceiverRegistered) {
            unregisterReceiver(screenStateReceiver)
            screenStateReceiverRegistered = false
        }
        notificationStateJob?.cancel()
        qteTypeIconSampling.value?.stop()
        qteTypeIconSampling.value = null
        Log.i("LIFECYCLE_DIAGNOSTIC", "[PID=${android.os.Process.myPid()}] [Instance=${System.identityHashCode(this)}] DroidballService.onDestroy()")
        if (activeService === this) activeService = null
        Log.d("DroidballService", "onDestroy: Releasing resources")
        markStopped()
        if (registrationMode) registrationController.stop()
        else fieldViewModel.fieldTeamEditor.close()
        registrationBoxes?.close()
        registrationBoxes = null
        qteCalibrationWindow?.close()
        qteCalibrationWindow = null
        retainedOpponentBadge.close()
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
            capturedOverlayRegion = null
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        showIdleDroidballNotification(this)
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
    data object EditTeamRequested : DroidballSignal()
    data object RestartObservationRequested : DroidballSignal()
    data object IgnoreCurrentScreenRequested : DroidballSignal()
    data object StopSessionRequested : DroidballSignal()
    data object ConfirmInferredPlayerTeamRequested : DroidballSignal()
    data object RejectInferredPlayerTeamRequested : DroidballSignal()
    data object BeginNextMatch : DroidballSignal()
    /** A stale notification cannot close the replacement match. */
    data class BeginNextMatchRequested(val matchId: String) : DroidballSignal()
    data class VisualCaptureGap(val durationNanos: Long) : DroidballSignal()
}
