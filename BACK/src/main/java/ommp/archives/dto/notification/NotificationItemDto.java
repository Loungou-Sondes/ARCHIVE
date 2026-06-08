package ommp.archives.dto.notification;

import java.time.Instant;

public record NotificationItemDto(
	Long id,
	String typeCode,
	Long bordereauId,
	String numeroBordereau,
	String message,
	Instant createdAt
) {
}
