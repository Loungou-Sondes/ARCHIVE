package ommp.archives.dto.boite;

/** Fiche consultation d'une boîte (recherche, admin). */
public record BoiteConsultationDto(
	Long boiteId,
	String titre,
	String motsCles,
	String contenu,
	int anneeMin,
	int anneeMax,
	int metrageCm,
	String documentTypeTitle,
	Long bordereauId,
	String numeroBordereau,
	String directionLabel,
	int nombreBoitesBordereau,
	String typeEtat,
	String typeEtatLabel,
	String emplacement
) {
}
