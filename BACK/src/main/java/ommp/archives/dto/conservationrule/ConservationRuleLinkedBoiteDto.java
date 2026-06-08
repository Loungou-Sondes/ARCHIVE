package ommp.archives.dto.conservationrule;

public record ConservationRuleLinkedBoiteDto(
	Long id,
	String titre,
	int anneeMin,
	int anneeMax,
	int metrageCm
) {
}
