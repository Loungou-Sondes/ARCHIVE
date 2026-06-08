package ommp.archives.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
    @Size(max = 255, message = "Email trop long")
    String email,

    @Size(max = 255, message = "Telephone trop long")
    String phoneNumber
) {
}