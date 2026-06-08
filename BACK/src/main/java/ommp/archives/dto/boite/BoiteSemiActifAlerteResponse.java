package ommp.archives.dto.boite;

public record BoiteSemiActifAlerteResponse(
	Long boiteId,
	String boiteTitre,
	Long bordereauId,
	String numeroBordereau,
	Long regleId,
	String regleReference,
	Long documentTypeId,
	String documentTypeTitle,
	String finalDecision,
	Integer anneeAffichage,
	boolean actionEcheance
) {
}
