package com.gongity.iqlizer

import android.service.notification.NotificationListenerService

class MediaNotificationListener : NotificationListenerService() {
    // We don't need to do anything here, we just need to be an enabled listener
    // for MediaSessionManager.getActiveSessions() to work without a privileged permission.
}
