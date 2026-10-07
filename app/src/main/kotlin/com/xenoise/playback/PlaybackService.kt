package com.xenoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.xenoise.MainActivity
import com.xenoise.R
import com.xenoise.XenoiseApplication
import com.xenoise.model.NoiseColors
import com.xenoise.model.Slopes
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

/**
 * Keeps the noise playing with the screen off. It runs in the foreground for as long as
 * the player's session is active, shows a media notification with play, pause and stop,
 * publishes a media session for the lock screen, holds a partial wake lock while sound
 * is playing, and pauses when headphones are unplugged.
 *
 * The audio itself lives in [NoisePlayer]; this service only mirrors its state.
 */
class PlaybackService : Service() {

    private lateinit var player: NoisePlayer
    private lateinit var session: MediaSession
    private lateinit var notificationManager: NotificationManager
    private val scope = MainScope()

    private var wakeLock: PowerManager.WakeLock? = null
    private var inForeground = false
    private var startCommandSeen = false
    private var lastStartId = 0
    private var noisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) player.pause()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        player = (application as XenoiseApplication).player
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()

        session = MediaSession(this, "Xenoise").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    player.play()
                }

                override fun onPause() {
                    player.pause()
                }

                override fun onStop() {
                    player.stop()
                }

                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == CUSTOM_ACTION_STOP) player.stop()
                }
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }

        scope.launch {
            player.state
                .map { NotificationModel.from(it) }
                .distinctUntilChanged()
                .conflate()
                .collect {
                    render(it)
                    // Android drops notification updates past a few per second, and a dropped
                    // last update would leave the notification stale. Pace them instead.
                    delay(NOTIFICATION_MIN_INTERVAL_MS)
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        startCommandSeen = true
        lastStartId = startId
        when (intent?.action) {
            ACTION_TOGGLE -> player.togglePlayPause()
            ACTION_STOP -> player.stop()
        }
        val model = NotificationModel.from(player.state.value)
        // A start through startForegroundService must be followed by startForeground,
        // even if the session already ended in the meantime.
        if (intent?.action == ACTION_START && !inForeground) goForeground(model)
        if (!model.isSessionActive) shutDown()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        releaseWakeLock()
        unregisterNoisyReceiver()
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    private fun render(model: NotificationModel) {
        if (!model.isSessionActive) {
            // Before the first start command, leave shutting down to onStartCommand: it must
            // call startForeground first, or Android treats the service as misbehaving.
            if (startCommandSeen) shutDown()
            return
        }
        updateSession(model)
        val notification = buildNotification(model)
        if (inForeground) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } else {
            goForeground(model, notification)
        }
        if (model.isPlaying) {
            acquireWakeLock()
            registerNoisyReceiver()
        } else {
            releaseWakeLock()
            unregisterNoisyReceiver()
        }
    }

    private fun goForeground(model: NotificationModel, notification: Notification = buildNotification(model)) {
        // Android 13+ builds the media controls from the session, so fill it in first.
        if (model.isSessionActive) updateSession(model)
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            inForeground = true
        } catch (e: Exception) {
            // Android 12+ refuses foreground starts from the background. Nothing to show then.
            Log.w(TAG, "Could not enter the foreground", e)
        }
    }

    private fun shutDown() {
        // Clear the flag first so a play() that races with this starts a fresh service.
        running = false
        releaseWakeLock()
        unregisterNoisyReceiver()
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        // Stopping by start id keeps the service alive if a newer start already arrived.
        stopSelf(lastStartId)
    }

    private fun updateSession(model: NotificationModel) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, model.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitle(model))
                .putBitmap(MediaMetadata.METADATA_KEY_ART, Artwork.forSlope(model.artSlope))
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (model.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (model.isPlaying) 1f else 0f,
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(
                        CUSTOM_ACTION_STOP,
                        getString(R.string.action_stop),
                        R.drawable.ic_notification_stop,
                    ).build()
                )
                .build()
        )
    }

    private fun buildNotification(model: NotificationModel): Notification {
        val playPause = if (model.isPlaying) {
            action(R.drawable.ic_notification_pause, R.string.action_pause, ACTION_TOGGLE)
        } else {
            action(R.drawable.ic_notification_play, R.string.action_play, ACTION_TOGGLE)
        }
        val stop = action(R.drawable.ic_notification_stop, R.string.action_stop, ACTION_STOP)

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(Artwork.forSlope(model.artSlope))
            .setContentTitle(model.title)
            .setContentText(subtitle(model))
            .setColor(NoiseColors.forSlope(model.artSlope))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setOngoing(model.isPlaying)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(playPause)
            .addAction(stop)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun subtitle(model: NotificationModel): String {
        val timerEnd = model.timerEndsAtElapsedMs
        return when {
            model.isPreviewing -> getString(R.string.notification_editing, Slopes.format(model.slope))
            timerEnd != null -> {
                val wallClock = System.currentTimeMillis() + (timerEnd - SystemClock.elapsedRealtime())
                val time = DateFormat.getTimeFormat(this).format(Date(wallClock))
                getString(R.string.notification_timer, time)
            }
            else -> getString(R.string.notification_slope, Slopes.format(model.slope))
        }
    }

    private fun action(icon: Int, title: Int, intentAction: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(this, icon),
            getString(title),
            serviceIntent(intentAction),
        ).build()

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_playback_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val lock = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xenoise:playback")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        // Held only while sound plays; released on pause and stop.
        if (!lock.isHeld) lock.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyReceiverRegistered = false
    }

    /** The parts of [PlayerState] the notification shows, so it only redraws when they change. */
    private data class NotificationModel(
        val isSessionActive: Boolean,
        val isPlaying: Boolean,
        val isPreviewing: Boolean,
        val title: String,
        val slope: Float,
        /** Rounded so dragging the editor line does not regenerate artwork on every frame. */
        val artSlope: Float,
        val timerEndsAtElapsedMs: Long?,
    ) {
        companion object {
            fun from(state: PlayerState) = NotificationModel(
                isSessionActive = state.isSessionActive,
                isPlaying = state.isPlaying,
                isPreviewing = state.isPreviewing,
                title = state.noise.displayName,
                slope = state.noise.slope,
                artSlope = state.noise.slope.roundToInt().toFloat(),
                timerEndsAtElapsedMs = state.timer?.endsAtElapsedMs,
            )
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_MIN_INTERVAL_MS = 250L
        private const val ACTION_START = "com.xenoise.action.START"
        private const val ACTION_TOGGLE = "com.xenoise.action.TOGGLE"
        private const val ACTION_STOP = "com.xenoise.action.STOP"
        private const val CUSTOM_ACTION_STOP = "com.xenoise.custom.STOP"

        @Volatile
        private var running = false

        /** Starts the service in the foreground unless it is already running. */
        fun ensureRunning(context: Context) {
            if (running) return
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_START)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start the playback service", e)
            }
        }
    }
}
