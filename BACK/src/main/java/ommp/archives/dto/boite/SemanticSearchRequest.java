package ommp.archives.dto.boite;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SemanticSearchRequest(
	@NotBlank @Size(max = 500) String query,
	/** Épi courant (ex. {@code 01}) — filtre les blocs à surligner en vue 3D. */
	String epiNumero,
	Integer limit
) {
}
