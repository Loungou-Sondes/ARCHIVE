package ommp.archives.dto.bordereau;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBordereauRequest(
	@JsonProperty("directionId") @Size(max = 64) String directionId,
	@JsonProperty("dateTransfert") @NotNull LocalDate dateTransfert,
	@JsonProperty("observation") @Size(max = 2000) String observation,
	/** Si {@code true}, statut {@code EN_ATTENTE} (page Alertes) ; sinon {@code AFFECTE} (liste principale). */
	@JsonProperty("mettreEnAttente") Boolean mettreEnAttente,
	@JsonProperty("boites") @NotEmpty @Valid List<CreateBordereauBoiteRequest> boites
) {
}
