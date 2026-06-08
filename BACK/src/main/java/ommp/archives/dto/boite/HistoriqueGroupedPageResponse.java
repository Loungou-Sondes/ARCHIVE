package ommp.archives.dto.boite;

import java.util.List;

/** Page d’historique regroupée par bordereau. */
public record HistoriqueGroupedPageResponse(
	List<BordereauHistoriqueGroupDto> content,
	long totalElements,
	long totalBoites,
	int totalPages,
	int number,
	int size
) {
}
