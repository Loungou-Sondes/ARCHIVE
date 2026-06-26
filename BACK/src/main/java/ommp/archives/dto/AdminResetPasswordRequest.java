package ommp.archives.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminResetPasswordRequest(
	@NotBlank(message = "Mot de passe requis")
	@Size(min = 4, max = 255, message = "Mot de passe invalide")
	String newPassword,

	@NotBlank(message = "Confirmation requise")
	@Size(min = 4, max = 255, message = "Confirmation invalide")
	String confirmPassword
) {
}
