package ommp.archives.dto.boite;

/** Boîte en fin de cycle (transfert ou destruction) dans un groupe bordereau. */
public record BoiteHistoriqueItemDto(
	Long boiteId,
	String boiteTitre,
	String documentTypeTitle,
	/** {@code TRANSFERT} ou {@code DESTRUCTION}. */
	String typeEtat,
	String dateEtat,
	String utilisateur,
	String dernierEmplacement,
	String regleReference,
	String finalDecision
) {
}
