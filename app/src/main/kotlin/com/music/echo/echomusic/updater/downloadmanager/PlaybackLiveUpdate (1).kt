package echo.music.iad1tya.echomusic.updater.downloadmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.R
import echo.music.iad1tya.playback.MusicService

/**
 * Posts a second, non-media "Live Update" notification so Origin Island / Android 16 can show
 * now-playing (media-style notifications are not promoted). Shows artwork, title, artist, a
 * progress bar and Previous / Play-Pause / Next / Like / Shuffle actions.
 */
class PlaybackLiveUpdate(context: Context, private val service: MusicService?) {

  private val appContext = context.applicationContext
  private val nm = appContext.getSystemService(NotificationManager::class.java)
  private val handler = Handler(Looper.getMainLooper())
  private var player: Player? = null
  private var artwork: Icon? = null
  private var artworkForId: String? = null

  private val ticker =
    object : Runnable {
      override fun run() {
        val p = player ?: return
        if (p.isPlaying) {
          post(p)
          handler.postDelayed(this, TICK_MS)
        }
      }
    }

  private val receiver =
    object : BroadcastReceiver() {
      override fun onReceive(c: Context, intent: Intent) {
        val p = player ?: return
        try {
          when (intent.action) {
            ACTION_PREV -> p.seekToPrevious()
            ACTION_NEXT -> p.seekToNext()
            ACTION_PLAY_PAUSE -> if (p.isPlaying) p.pause() else p.play()
            ACTION_SHUFFLE -> p.shuffleModeEnabled = !p.shuffleModeEnabled
            ACTION_LIKE -> service?.toggleLike()
          }
        } catch (e: Exception) {}
        // Refresh quickly so the buttons reflect the new state (like needs a moment to save).
        handler.postDelayed({ player?.let { post(it) } }, 700)
      }
    }

  init {
    try {
      nm?.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Now playing (live)", NotificationManager.IMPORTANCE_LOW)
      )
    } catch (e: Exception) {}
    try {
      activeReceiver?.let { appContext.unregisterReceiver(it) }
    } catch (e: Exception) {}
    try {
      val filter =
        IntentFilter().apply {
          addAction(ACTION_PREV)
          addAction(ACTION_PLAY_PAUSE)
          addAction(ACTION_NEXT)
          addAction(ACTION_LIKE)
          addAction(ACTION_SHUFFLE)
        }
      ContextCompat.registerReceiver(
        appContext,
        receiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      activeReceiver = receiver
    } catch (e: Exception) {}
  }

  /** Artwork the media notification loaded, so the island shows the same cover. */
  fun setArtwork(icon: Icon?) {
    if (icon == null) return
    artwork = icon
    artworkForId = player?.currentMediaItem?.mediaId
  }

  /** Call whenever the player state may have changed. */
  fun sync(p: Player) {
    player = p
    handler.removeCallbacks(ticker)
    val hasTrack =
      p.currentMediaItem != null &&
        p.playbackState != Player.STATE_IDLE &&
        p.playbackState != Player.STATE_ENDED
    if (!hasTrack) {
      try {
        nm?.cancel(NOTIFICATION_ID)
      } catch (e: Exception) {}
      return
    }
    post(p)
    if (p.isPlaying) handler.postDelayed(ticker, TICK_MS)
  }

  private fun action(iconRes: Int, label: String, action: String, code: Int): Notification.Action {
    val pi =
      PendingIntent.getBroadcast(
        appContext,
        code,
        Intent(action).setPackage(appContext.packageName),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    return Notification.Action.Builder(Icon.createWithResource(appContext, iconRes), label, pi)
      .build()
  }

  private fun post(p: Player) {
    try {
      val title = p.mediaMetadata.title?.toString()
      if (title.isNullOrBlank()) return
      val playing = p.isPlaying
      val position = p.currentPosition.coerceAtLeast(0L)
      val duration = p.duration
      val liked = service?.isCurrentSongLiked() == true

      val tap =
        PendingIntent.getActivity(
          appContext,
          0,
          Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

      val builder =
        Notification.Builder(appContext, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_launcher_nobg)
          .setContentTitle(title)
          .setContentText(p.mediaMetadata.artist?.toString())
          .setContentIntent(tap)
          .setOngoing(true)
          .setOnlyAlertOnce(true)
          .setVisibility(Notification.VISIBILITY_PUBLIC)
          .setTimeoutAfter(if (playing) TIMEOUT_PLAYING_MS else TIMEOUT_PAUSED_MS)

      if (playing) {
        builder
          .setShowWhen(true)
          .setUsesChronometer(true)
          .setWhen(System.currentTimeMillis() - position)
      } else {
        builder.setShowWhen(false)
      }

      val art = if (artworkForId == p.currentMediaItem?.mediaId) artwork else null
      if (art != null) builder.setLargeIcon(art)

      builder.addAction(action(R.drawable.skip_previous, "Previous", ACTION_PREV, 1))
      builder.addAction(
        action(
          if (playing) R.drawable.pause else R.drawable.play,
          if (playing) "Pause" else "Play",
          ACTION_PLAY_PAUSE,
          2,
        )
      )
      builder.addAction(action(R.drawable.skip_next, "Next", ACTION_NEXT, 3))
      builder.addAction(
        action(
          if (liked) R.drawable.favorite else R.drawable.favorite_border,
          "Like",
          ACTION_LIKE,
          4,
        )
      )
      builder.addAction(
        action(
          if (p.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
          "Shuffle",
          ACTION_SHUFFLE,
          5,
        )
      )

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && duration > 0) {
        val percent = ((position * 100) / duration).toInt().coerceIn(0, 100)
        builder.setStyle(
          Notification.ProgressStyle()
            .addProgressSegment(Notification.ProgressStyle.Segment(100))
            .setProgress(percent)
        )
      }

      builder.extras.putBoolean("android.requestPromotedOngoing", true)

      nm?.notify(NOTIFICATION_ID, builder.build())
    } catch (e: Exception) {}
  }

  private companion object {
    const val CHANNEL_ID = "echo_live_playback"
    const val NOTIFICATION_ID = 889
    const val TICK_MS = 5_000L
    const val TIMEOUT_PLAYING_MS = 15_000L
    const val TIMEOUT_PAUSED_MS = 300_000L
    const val ACTION_PREV = "echo.music.iad1tya.live.PREV"
    const val ACTION_PLAY_PAUSE = "echo.music.iad1tya.live.PLAY_PAUSE"
    const val ACTION_NEXT = "echo.music.iad1tya.live.NEXT"
    const val ACTION_LIKE = "echo.music.iad1tya.live.LIKE"
    const val ACTION_SHUFFLE = "echo.music.iad1tya.live.SHUFFLE"

    @Volatile var activeReceiver: BroadcastReceiver? = null
  }
}
