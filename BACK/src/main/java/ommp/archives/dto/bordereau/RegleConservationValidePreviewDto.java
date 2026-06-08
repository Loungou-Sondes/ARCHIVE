package ommp.archives.dto.bordereau;

/**
 * Aperçu de la règle de conservation <strong>valide</strong> pour un type de document
 * (formulaire bordereau / boîte).
 */
public record RegleConservationValidePreviewDto(
	String reference,
	String finalDecision,
	boolean activeUnknown,
	Integer activeYears,
	boolean semiActiveUnknown,
	Integer semiActiveYears
) {
}
