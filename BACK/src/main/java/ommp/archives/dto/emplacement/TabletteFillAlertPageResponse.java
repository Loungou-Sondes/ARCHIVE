package ommp.archives.dto.emplacement;

import java.util.List;

public record TabletteFillAlertPageResponse(
	List<TabletteFillAlertDto> content,
	long totalElements,
	int totalPages,
	int page,
	int size
) {
}
