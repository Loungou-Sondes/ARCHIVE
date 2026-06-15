package ommp.archives.dto.boite;

/**
 * Ligne paginée du centre d'alertes archives (semi-actif puis échéance transfert/destruction).
 */
public record BoiteAlerteArchivesRow(
	/** {@code SEMI_ACTIF} ou {@code ECHEANCE}. */
	String kind,
	BoiteSemiActifAlerteResponse semiActif,
	BoiteEcheanceAlerteResponse echeance
) {
	public static BoiteAlerteArchivesRow semiActif(BoiteSemiActifAlerteResponse row) {
		return new BoiteAlerteArchivesRow("SEMI_ACTIF", row, null);
	}

	public static BoiteAlerteArchivesRow echeance(BoiteEcheanceAlerteResponse row) {
		return new BoiteAlerteArchivesRow("ECHEANCE", null, row);
	}
}
