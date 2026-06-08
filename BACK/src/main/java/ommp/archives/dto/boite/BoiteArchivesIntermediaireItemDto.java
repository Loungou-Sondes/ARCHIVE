package ommp.archives.dto.boite;

/** Boîte en archive intermédiaire (état documentaire semi-actif, bordereau affecté). */
public record BoiteArchivesIntermediaireItemDto(
	Long boiteId,
	String boiteTitre,
	String documentTypeTitle,
	String dateEtat,
	String emplacement,
	int anneeMin,
	int anneeMax,
	String regleReference,
	String finalDecision
) {
}
