package ommp.archives.dto.bordereau;

public record BordereauListItemResponse(
	Long id,
	String numeroBordereau,
	String dateTransfert,
	String directionId,
	String directionLabel,
	String agentUserName,
	int boitesCount,
	String observation,
	/** Au moins une boîte est liée à une règle valide avec durée active inconnue. */
	boolean regleDureeActiveInconnue,
	/** Références des règles concernées (affichage alerte). */
	String reglesEnAlerteResume
) {
}
