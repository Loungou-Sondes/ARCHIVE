package ommp.archives.dto.emplacement;

public record BlocDto(
	String id,
	int positionIndex,
	/** Code métier auto : {@code épi + travée + ligne + bloc} (ex. {@code 01111}). */
	String numero,
	/** {@code null} si le bloc est libre ; sinon identifiant de la boîte qui l’occupe. */
	Long boiteId,
	/** Présent uniquement si {@link #boiteId()} n’est pas {@code null}. */
	BlocBoiteSummaryDto boite
) {
}
