package ommp.archives.dto.boite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDossierRequest(
	@NotBlank @Size(max = 500) String titre,
	@Size(max = 4000) String contenu,
	@NotNull @Min(1800) @Max(2100) Integer annee
) {
}
