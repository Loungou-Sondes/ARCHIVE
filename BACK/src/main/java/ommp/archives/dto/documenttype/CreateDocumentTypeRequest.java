package ommp.archives.dto.documenttype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDocumentTypeRequest(
	@NotBlank @Size(max = 500) String title,
	@Size(max = 64) String directionId
) {
}
