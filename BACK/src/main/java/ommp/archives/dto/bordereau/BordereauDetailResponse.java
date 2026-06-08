package ommp.archives.dto.bordereau;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BordereauDetailResponse(
	Long id,
	String numeroBordereau,
	String dateTransfert,
	String directionId,
	String directionLabel,
	String agentUserName,
	String observation,
	String statut,
	boolean regleDureeActiveInconnue,
	String reglesEnAlerteResume,
	/** Nombre de boîtes enregistrées sur le bordereau ({@code BORDEREAUX.NOMBRE_BOITES}). */
	@JsonProperty("nombreBoites") int nombreBoites,
	List<BoiteResponse> boites
) {
}
