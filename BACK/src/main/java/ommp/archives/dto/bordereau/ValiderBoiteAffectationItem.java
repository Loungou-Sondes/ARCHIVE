package ommp.archives.dto.bordereau;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record ValiderBoiteAffectationItem(
	@NotNull Long boiteId,
	List<String> emplacementBlocIds,
	Integer renseignerAnneesActives
) {
}
