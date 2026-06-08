package ommp.archives.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
	@NotBlank(message = "Nom d'utilisateur requis")
	@Size(max = 255)
	String username,

	@NotBlank(message = "Mot de passe requis")
	@Size(min = 4, max = 255, message = "Mot de passe invalide")
	String newPassword,

	@NotBlank(message = "Confirmation requise")
	@Size(min = 4, max = 255, message = "Confirmation invalide")
	String confirmPassword
) {
}
