package ommp.archives.dto.emplacement;

import java.util.List;

public record EpiListPageResponse(
	List<EpiSummaryDto> content,
	long totalElements,
	int totalPages,
	int page,
	int size,
	long totalLinearCm
) {
}
