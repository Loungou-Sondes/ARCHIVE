package ommp.archives.dto.emplacement;

/** Alerte : tablette (ligne) dont le taux de remplissage dépasse le seuil. */
public record TabletteFillAlertDto(
	String tabletteId,
	String epiId,
	String epiNumero,
	String tabletteNumero,
	/** Libellé affiché, ex. « Ligne 04 ». */
	String rowLabel,
	int linearCm,
	int occupiedCount,
	int totalCount,
	int fillPercent
) {
}
