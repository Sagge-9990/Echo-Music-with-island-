package echo.music.iad1tya.echomusic.updater.downloadmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import echo.music.iad1tya.MainActivity
import echo.music.iad1tya.R

/**
 * Posts a second, non-media "Live Update" notification while music plays, so Origin Island /
 * Android 16 can show it (media-style notifications are not promoted).
 */
class PlaybackLiveUpdate(context: Context) {

  private val appContext = context.applicationContext
  private val nm = appContext.getSystemService(NotificationManager::class.java)
  private val handler = Handler(Looper.getMainLooper())
  private var player: Player? = null

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

  init {
    try {
      nm?.createNotificationChannel(
        NotificationChannel(CHANNEL_ID, "Now playing (live)", NotificationManager.IMPORTANCE_LOW)
      )
    } catch (e: Exception) {}
  }

  /** Call whenever the player state may have changed. */
  fun sync(p: Player) {
    player = p
    handler.removeCallbacks(ticker)
    if (p.isPlaying) {
      post(p)
      handler.postDelayed(ticker, TICK_MS)
    } else {
      try {
        nm?.cancel(NOTIFICATION_ID)
      } catch (e: Exception) {}
    }
  }

  private fun post(p: Player) {
    try {
      val title = p.mediaMetadata.title?.toString()
      if (title.isNullOrBlank()) return
      val position = p.currentPosition.coerceAtLeast(0L)
      val duration = p.duration

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
          .setShowWhen(true)
          .setUsesChronometer(true)
          .setWhen(System.currentTimeMillis() - position)
          .setVisibility(Notification.VISIBILITY_PUBLIC)
          .setTimeoutAfter(TIMEOUT_MS)

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
    const val TIMEOUT_MS = 15_000L
  }
}
