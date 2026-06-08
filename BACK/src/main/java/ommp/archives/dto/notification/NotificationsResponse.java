package ommp.archives.dto.notification;

import java.util.List;

public record NotificationsResponse(
	long unreadCount,
	List<NotificationItemDto> items
) {
}
