package ommp.archives.dto.boite;

import java.util.List;

public record SemanticSearchResponse(
	String query,
	List<SemanticSearchHitDto> results,
	List<String> highlightBlocIds
) {
}
