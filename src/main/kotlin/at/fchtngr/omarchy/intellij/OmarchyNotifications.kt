package at.fchtngr.omarchy.intellij

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

object OmarchyNotifications {
    fun info(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Omarchy Notifications")
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(null)
    }

    fun warning(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Omarchy Notifications")
            .createNotification(title, content, NotificationType.WARNING)
            .notify(null)
    }
}
