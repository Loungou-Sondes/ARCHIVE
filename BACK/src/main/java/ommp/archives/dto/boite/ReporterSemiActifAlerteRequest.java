package ommp.archives.dto.boite;

import jakarta.validation.constraints.NotNull;

public record ReporterSemiActifAlerteRequest(
	@NotNull Integer annee
) {
}
