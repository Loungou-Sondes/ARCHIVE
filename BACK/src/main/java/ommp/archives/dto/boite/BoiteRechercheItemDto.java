package ommp.archives.dto.boite;

/** Ligne résultat — recherche transversale sur toutes les boîtes. */
public record BoiteRechercheItemDto(
	Long boiteId,
	String titre,
	String motsCles,
	int anneeMin,
	int anneeMax,
	String documentTypeTitle,
	Long bordereauId,
	String numeroBordereau,
	String directionLabel,
	String typeEtat,
	String typeEtatLabel,
	String emplacement
) {
}
