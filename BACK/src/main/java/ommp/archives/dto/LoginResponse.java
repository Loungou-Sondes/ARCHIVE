package ommp.archives.dto;

import java.util.List;

/** Réponse login : pas de jeton dans le corps (cookie httpOnly uniquement). */
public record LoginResponse(
	long expiresIn,
	String username,
	List<String> roles
) {
}
