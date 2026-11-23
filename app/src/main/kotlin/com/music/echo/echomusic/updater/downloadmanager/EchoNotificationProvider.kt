package echo.music.iad1tya.echomusic.updater.downloadmanager

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

@OptIn(UnstableApi::class)
class EchoNotificationProvider(
  context: Context,
  notificationIdProvider: DefaultMediaNotificationProvider.NotificationIdProvider,
  channelId: String,
  channelNameResourceId: Int,
) : MediaNotification.Provider {

  private val defaultProvider =
    DefaultMediaNotificationProvider(
      context,
      notificationIdProvider,
      channelId,
      channelNameResourceId
    )

  private val liveUpdate = PlaybackLiveUpdate(context)

  fun setSmallIcon(iconResId: Int): EchoNotificationProvider {
    defaultProvider.setSmallIcon(iconResId)
    return this
  }

  override fun createNotification(
    mediaSession: MediaSession,
    customLayout: ImmutableList<CommandButton>,
    actionFactory: MediaNotification.ActionFactory,
    onNotificationChangedCallback: MediaNotification.Provider.Callback,
  ): MediaNotification {
    val mediaNotification =
      defaultProvider.createNotification(
        mediaSession,
        customLayout,
        actionFactory,
        onNotificationChangedCallback
      )

    val player = mediaSession.player
    val shouldBeOngoing =
      player.playWhenReady &&
        player.playbackState != Player.STATE_IDLE &&
        player.playbackState != Player.STATE_ENDED

    liveUpdate.sync(player)

    val notification = mediaNotification.notification
    if (shouldBeOngoing) {
      notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT
    } else {
      notification.flags = notification.flags and Notification.FLAG_ONGOING_EVENT.inv()
    }

    // Ask Android 16 / Origin Island to show playback as a Live Update while playing.
    notification.extras.putBoolean("android.requestPromotedOngoing", shouldBeOngoing)
    if (shouldBeOngoing) {
      notification.extras.putCharSequence("android.shortCriticalText", "Playing")
    } else {
      notification.extras.remove("android.shortCriticalText")
    }

    return MediaNotification(mediaNotification.notificationId, notification)
  }

  override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
    defaultProvider.handleCustomCommand(session, action, extras)
}
