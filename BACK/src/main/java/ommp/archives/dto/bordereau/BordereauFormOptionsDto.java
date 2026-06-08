package ommp.archives.dto.bordereau;

import java.util.List;

import ommp.archives.dto.documenttype.DirectionOptionDto;
import ommp.archives.dto.documenttype.DocumentTypeResponse;

public record BordereauFormOptionsDto(
	List<DirectionOptionDto> directions,
	List<DocumentTypeResponse> documentTypes,
	/** Prochain numéro affiché pour {@code anneeProchainNumero} (ex. {@code 1-2026}). */
	String prochainNumeroAffiche,
	/** Direction de l’agent connecté (profil), si connue. */
	String userDirectionId,
	String userDirectionLabel,
	/** {@code true} pour les agents : direction préremplie et non modifiable. */
	boolean directionLocked
) {
}
