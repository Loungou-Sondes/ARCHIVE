package ommp.archives.dto.boite;

import java.util.List;

public record SemanticSearchHitDto(
	Long boiteId,
	String titre,
	double score,
	String epiNumero,
	String emplacement,
	List<String> blocIds,
	boolean onRequestedEpi
) {
}
