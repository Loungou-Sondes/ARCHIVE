package ommp.archives.dto.boite;

import java.util.List;

/** Bordereau avec les boîtes transférées ou détruites qui lui sont rattachées. */
public record BordereauHistoriqueGroupDto(
	Long bordereauId,
	String numeroBordereau,
	String dateTransfert,
	String directionLabel,
	String agentUserName,
	int boitesCount,
	List<BoiteHistoriqueItemDto> boites
) {
}
