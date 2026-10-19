package in.codebasket.cbwa.common.notification;

import java.time.Instant;

public record NotificationDto(Long id, String type, String severity, String title, String body, String link,
                              boolean isRead, Instant createdAt) {

    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getBody(), n.getLink(),
                n.isRead(), n.getCreatedAt());
    }
}
