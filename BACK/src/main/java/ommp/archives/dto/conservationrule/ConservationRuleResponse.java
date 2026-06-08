package ommp.archives.dto.conservationrule;

public record ConservationRuleResponse(
	Long id,
	String reference,
	DocumentTypeMiniDto documentType,
	boolean activeUnknown,
	Integer activeYears,
	boolean semiActiveUnknown,
	Integer semiActiveYears,
	String finalDecision,
	String status,
	/** Toujours {@code false} — les règles ne passent plus par le hub Alertes. */
	boolean durationAlert
) {
}
