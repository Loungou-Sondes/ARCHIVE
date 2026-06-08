package ommp.archives.dto;

public record UserManagementResponse(
    String id,
    String userName,
    String firstName,
    String email,
    Integer gender,
    String phoneNumber,
    String role,
    String harbor,
    String userRegistrationNumber
) {
}
