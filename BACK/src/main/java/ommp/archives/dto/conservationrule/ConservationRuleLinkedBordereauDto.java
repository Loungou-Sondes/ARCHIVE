package ommp.archives.dto.conservationrule;

import java.util.List;

public record ConservationRuleLinkedBordereauDto(
	Long bordereauId,
	String numeroAffiche,
	List<ConservationRuleLinkedBoiteDto> boites
) {
}
