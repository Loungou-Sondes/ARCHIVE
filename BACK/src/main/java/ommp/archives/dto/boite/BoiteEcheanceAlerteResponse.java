package ommp.archives.dto.boite;

public record BoiteEcheanceAlerteResponse(
	Long boiteId,
	String boiteTitre,
	Long bordereauId,
	String numeroBordereau,
	Long regleId,
	String regleReference,
	String documentTypeTitle,
	String finalDecision,
	/** Année de destruction ou de transfert : année max + années semi-actives. */
	int anneeEcheance
) {
}
