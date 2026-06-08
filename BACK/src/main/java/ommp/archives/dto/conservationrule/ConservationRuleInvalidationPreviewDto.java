package ommp.archives.dto.conservationrule;

import java.util.List;

public record ConservationRuleInvalidationPreviewDto(
	Long ruleId,
	String reference,
	Long documentTypeId,
	String documentTypeTitle,
	long totalBoites,
	List<ConservationRuleLinkedBordereauDto> bordereaux
) {
}
