package ommp.archives.dto;

public record UserProfileResponse(
    String userName,
    String email,
    String phoneNumber,
    String role,
    boolean passwordResetRequested
) {
}