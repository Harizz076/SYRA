package com.wboelens.polarrecorder.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * A NotificationListenerService that enables MediaSessionManager access.
 *
 * This service must be declared in the manifest and the user must grant
 * "Notification Access" permission in system settings. Once granted,
 * MediaPlaybackManager can use this service's ComponentName to listen
 * for active media sessions from any music app on the device.
 *
 * This service does NOT process notifications directly — it simply provides
 * the system-level access required by MediaSessionManager.
 */
class MediaPlaybackListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "MediaPlaybackListener"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListenerService connected — media session access enabled")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListenerService disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // No-op: we don't need to process individual notifications.
        // MediaPlaybackManager handles media sessions directly.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // No-op
    }
}
