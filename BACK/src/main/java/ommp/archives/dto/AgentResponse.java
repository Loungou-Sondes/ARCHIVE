package ommp.archives.dto;

import java.time.Instant;

/**
 * Vue agent = compte {@code USERS} enrichi avec {@code USER_DETAILS} (même matricule).
 */
public record AgentResponse(
	String id,
	String userName,
	String email,
	Integer gender,
	String phoneNumber,
	String role,
	String userRegistrationNumber,
	String lastName,
	String firstName,
	String cin,
	Instant birthDate,
	Instant recruitmentDate,
	Integer jobId,
	String positionId,
	Long statusId,
	String directionId,
	/** Libellé port — colonne optionnelle {@code USERS.HARBOR}. */
	String port,
	boolean passwordResetRequested
) {
}
