package ommp.archives.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Enveloppe JSON unique pour toutes les erreurs API (pas d’entités JPA).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
	Instant timestamp,
	int status,
	String error,
	String code,
	String message,
	Map<String, String> fieldErrors
) {

	public static ErrorResponse of(
		int status,
		String error,
		String code,
		String message,
		Map<String, String> fieldErrors
	) {
		return new ErrorResponse(
			Instant.now(),
			status,
			error,
			code,
			message,
			fieldErrors == null || fieldErrors.isEmpty()
				? Collections.emptyMap()
				: Map.copyOf(fieldErrors));
	}

	public static ErrorResponse of(int status, String error, String code, String message) {
		return of(status, error, code, message, Collections.emptyMap());
	}
}

