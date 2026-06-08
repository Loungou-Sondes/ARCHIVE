package ommp.archives.dto.boite;

/** Dossier documentaire lié à une boîte (table {@code DOSSIERS}). */
public record DossierResponse(
	Long id,
	Long boiteId,
	String titre,
	String contenu,
	int annee,
	String createdAt
) {
}
