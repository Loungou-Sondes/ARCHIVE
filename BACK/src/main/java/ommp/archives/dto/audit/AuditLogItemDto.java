package ommp.archives.dto.audit;

import java.time.Instant;

public record AuditLogItemDto(
	Long id,
	Instant createdAt,
	String userName,
	String actionCode,
	String resourceType,
	String detail
) {
}
