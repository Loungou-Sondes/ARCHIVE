package ommp.archives.dto.boite;

import java.util.List;

/** Page des archives intermédiaires regroupée par bordereau. */
public record ArchivesIntermediairesGroupedPageResponse(
	List<BordereauArchivesIntermediairesGroupDto> content,
	long totalElements,
	long totalBoites,
	int totalPages,
	int number,
	int size
) {
}
