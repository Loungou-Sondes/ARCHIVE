package ommp.archives.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
	@NotBlank(message = "Nom d'utilisateur requis")
	@Size(max = 255, message = "Nom d'utilisateur trop long")
	String username
) {
}
