package rem.receiver.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RemSoundService : Service() {
	companion object {
		const val CHANNEL_ID = "remsound_channel"
		const val NOTIFICATION_ID = 1
		const val ACTION_START = "ACTION_START"
		const val ACTION_STOP = "ACTION_STOP"
		const val ACTION_MUTE = "ACTION_MUTE"
		const val ACTION_SET_VOLUME = "ACTION_SET_VOLUME"
		const val EXTRA_PASSWORD = "EXTRA_PASSWORD"
		const val EXTRA_INSTANCE_ID = "EXTRA_INSTANCE_ID"
		const val EXTRA_TARGET_IP = "EXTRA_TARGET_IP"
		const val EXTRA_VOLUME = "EXTRA_VOLUME"
		const val EXTRA_BUFFER_MS = "EXTRA_BUFFER_MS"
		private val _isRunning = MutableStateFlow(false)
		val isRunning: StateFlow<Boolean> = _isRunning
	}

	private var receiver: RemReceiver? = null
	private var mediaSession: MediaSessionCompat? = null
	override fun onCreate() {
		super.onCreate()
		createNotificationChannel()
		val session = MediaSessionCompat(this, "RemSoundSession")
		session.isActive = true
		val stateBuilder = PlaybackStateCompat.Builder()
			.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_STOP)
			.setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
		session.setPlaybackState(stateBuilder.build())
		mediaSession = session
	}
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_STOP -> {
				receiver?.stop()
				receiver = null
				_isRunning.value = false
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					stopForeground(STOP_FOREGROUND_REMOVE)
				} else {
					@Suppress("DEPRECATION")
					stopForeground(true)
				}
				stopSelf()
				return START_NOT_STICKY
			}
			ACTION_MUTE -> {
				receiver?.sendControlPacket(RemoteControlKind.SystemMuteToggle)
			}
			ACTION_SET_VOLUME -> {
				val volume = intent.getFloatExtra(EXTRA_VOLUME, 100f)
				receiver?.setVolume(volume)
			}
			ACTION_START -> {
				if (receiver == null) {
					val password = intent.getStringExtra(EXTRA_PASSWORD) ?: ""
					val instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID) ?: ""
					val targetIp = intent.getStringExtra(EXTRA_TARGET_IP) ?: ""
					val volume = intent.getFloatExtra(EXTRA_VOLUME, 100f)
					val bufferMs = intent.getIntExtra(EXTRA_BUFFER_MS, 50)
					receiver = RemReceiver(password, instanceId).apply {
						setVolume(volume)
						setBufferMs(bufferMs)
					}
					receiver?.start(targetIp)
				}
			}
		}
		val notification = buildNotification()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
		} else {
			startForeground(NOTIFICATION_ID, notification)
		}
		_isRunning.value = true
		return START_STICKY
	}
	private fun buildNotification(): Notification {
		val mainIntent = Intent(this, MainActivity::class.java)
		val pendingMainIntent = PendingIntent.getActivity(
			this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		val stopIntent = Intent(this, RemSoundService::class.java).setAction(ACTION_STOP)
		val stopPending = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)
		val muteIntent = Intent(this, RemSoundService::class.java).setAction(ACTION_MUTE)
		val mutePending = PendingIntent.getService(this, 2, muteIntent, PendingIntent.FLAG_IMMUTABLE)
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_media_play)
			.setContentTitle("RemSound Receiver")
			.setContentText("Receiving PC Audio")
			.setContentIntent(pendingMainIntent)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setOngoing(true)
			.addAction(android.R.drawable.ic_lock_silent_mode_off, "Mute PC", mutePending)
			.addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
		mediaSession?.sessionToken?.let { token ->
			val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
				.setShowActionsInCompactView(0, 1)
				.setMediaSession(token)
			builder.setStyle(mediaStyle)
		}
		return builder.build()
	}
	private fun createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val channel = NotificationChannel(
				CHANNEL_ID,
				"RemSound Playback",
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = "Controls for background RemSound playback"
				setShowBadge(false)
			}
			val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			manager.createNotificationChannel(channel)
		}
	}
	override fun onDestroy() {
		super.onDestroy()
		receiver?.stop()
		receiver = null
		_isRunning.value = false
		mediaSession?.release()
		mediaSession = null
	}
	override fun onBind(intent: Intent?): IBinder? = null
}
