package ommp.archives.dto;

import java.util.List;

public record AgentListPageResponse(
	List<AgentResponse> content,
	long totalElements,
	int totalPages,
	int page,
	int size,
	long activeCount,
	long inactiveCount,
	long passwordResetPendingCount
) {
}
