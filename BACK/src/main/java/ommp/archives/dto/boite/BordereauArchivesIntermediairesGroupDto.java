package ommp.archives.dto.boite;

import java.util.List;

/** Bordereau affecté regroupant des boîtes semi-actives en magasin. */
public record BordereauArchivesIntermediairesGroupDto(
	Long bordereauId,
	String numeroBordereau,
	String dateTransfert,
	String directionLabel,
	String agentUserName,
	/** Boîtes encore semi-actives listées ici. */
	int boitesCount,
	/** Nombre total de boîtes sur le bordereau (y compris transférées / détruites). */
	int totalBoitesBordereau,
	List<BoiteArchivesIntermediaireItemDto> boites
) {
}
