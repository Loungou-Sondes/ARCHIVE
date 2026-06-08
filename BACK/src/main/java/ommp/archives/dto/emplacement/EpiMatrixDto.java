package ommp.archives.dto.emplacement;

import java.util.List;

public record EpiMatrixDto(
	EpiSummaryDto epi,
	List<TraversHeaderDto> traversHeaders,
	List<List<TabletteCellDto>> rows
) {
}
