package ommp.archives.exception;

import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Collectors;

import org.hibernate.JDBCException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.PersistenceException;

@RestControllerAdvice
public class RestExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> apiException(ApiException ex) {
		HttpStatus status = ex.getStatus();
		ErrorResponse body = ErrorResponse.of(
			status.value(),
			status.getReasonPhrase(),
			ex.getCode(),
			ex.getMessage());
		return ResponseEntity.status(status).body(body);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<ErrorResponse> badCredentials(BadCredentialsException ex) {
		return ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse.of(
				HttpStatus.UNAUTHORIZED.value(),
				HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				"INVALID_CREDENTIALS",
				"Identifiants incorrects."));
	}

	@ExceptionHandler(DisabledException.class)
	public ResponseEntity<ErrorResponse> disabledAccount(DisabledException ex) {
		return ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse.of(
				HttpStatus.UNAUTHORIZED.value(),
				HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				"ACCOUNT_INACTIVE",
				"Ce compte est inactif. Contactez un administrateur."));
	}

	@ExceptionHandler(UsernameNotFoundException.class)
	public ResponseEntity<ErrorResponse> usernameNotFound(UsernameNotFoundException ex) {
		// Même message générique que l’échec de connexion pour ne pas révéler l’existence du compte.
		return ResponseEntity
			.status(HttpStatus.UNAUTHORIZED)
			.body(ErrorResponse.of(
				HttpStatus.UNAUTHORIZED.value(),
				HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				"INVALID_CREDENTIALS",
				"Identifiants incorrects."));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> accessDenied(AccessDeniedException ex) {
		return ResponseEntity
			.status(HttpStatus.FORBIDDEN)
			.body(ErrorResponse.of(
				HttpStatus.FORBIDDEN.value(),
				HttpStatus.FORBIDDEN.getReasonPhrase(),
				"ACCESS_DENIED",
				"Accès refusé."));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> notReadable(HttpMessageNotReadableException ex) {
		log.warn("Corps JSON invalide ou types incompatibles", ex);
		ErrorResponse body = ErrorResponse.of(
			HttpStatus.BAD_REQUEST.value(),
			HttpStatus.BAD_REQUEST.getReasonPhrase(),
			"BAD_JSON",
			"Corps de requete JSON invalide (nombres entiers attendus pour la grille).");
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
			.collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a + "; " + b));
		String message = ex.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(FieldError::getDefaultMessage)
			.orElse("Requête invalide.");
		ErrorResponse body = ErrorResponse.of(
			HttpStatus.BAD_REQUEST.value(),
			HttpStatus.BAD_REQUEST.getReasonPhrase(),
			"VALIDATION_ERROR",
			message,
			fields);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> dataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		String root = ex.getMostSpecificCause() != null && ex.getMostSpecificCause().getMessage() != null
			? ex.getMostSpecificCause().getMessage()
			: "";
		String message = "Données invalides pour l’enregistrement (contrainte base de données).";
		if (root.contains("ORA-00001")) {
			message += " Contrainte d’unicité Oracle (souvent REFERENCE_REGLE) : redémarrez l’application "
				+ "(réparation auto) ou exécutez oracle-regles-conservation-suppression-unicite-reference-regle.sql "
				+ "(dossier src/main/resources/db/), puis réessayez.";
		}
		if (root.contains("ORA-02290") || root.toLowerCase().contains("check constraint")) {
			message += " Contrainte CHECK Oracle : voir les logs (ORA-02290, nom de contrainte). Scripts dans "
				+ "src/main/resources/db/ : oracle-regles-conservation-contrainte-statut.sql (STATUT), "
				+ "oracle-regles-conservation-contrainte-decision-finale.sql (DECISION_FINALE — utile si l’enregistrement "
				+ "échoue avec « Détruire » ou « Transférer »).";
		}
		ErrorResponse body = ErrorResponse.of(
			HttpStatus.BAD_REQUEST.value(),
			HttpStatus.BAD_REQUEST.getReasonPhrase(),
			"DATA_INTEGRITY_ERROR",
			message);
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(PersistenceException.class)
	public ResponseEntity<ErrorResponse> persistence(PersistenceException ex) {
		Throwable root = ex.getCause() != null ? ex.getCause() : ex;
		log.error("Erreur persistence JPA/Hibernate", ex);
		String detail = root.getMessage() != null ? root.getMessage() : ex.getMessage();
		ErrorResponse body = ErrorResponse.of(
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
			"PERSISTENCE_ERROR",
			truncateDetail(detail));
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ErrorResponse> dataAccess(DataAccessException ex) {
		Throwable root = ex.getMostSpecificCause();
		log.error("Erreur d'acces base de donnees", ex);
		String detail = root != null && root.getMessage() != null
			? root.getMessage()
			: "Voir les logs serveur.";
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(databaseErrorBody(detail));
	}

	/**
	 * Hibernate / JPA peuvent encapsuler une {@link SQLException} dans une
	 * {@link TransactionSystemException} sans la traduire en {@link DataAccessException},
	 * ce qui masquait la vraie cause (ex. Oracle ORA-xxxxx) derrière INTERNAL_ERROR.
	 */
	@ExceptionHandler(TransactionSystemException.class)
	public ResponseEntity<ErrorResponse> transactionSystem(TransactionSystemException ex) {
		log.error("Echec transaction (souvent SQL / Oracle)", ex);
		ResponseEntity<ErrorResponse> mapped = mapKnownCauses(ex);
		if (mapped != null) {
			return mapped;
		}
		Throwable root = ex.getMostSpecificCause();
		String detail = root.getMessage() != null ? root.getMessage() : ex.getMessage();
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(ErrorResponse.of(
				HttpStatus.INTERNAL_SERVER_ERROR.value(),
				HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
				"TRANSACTION_ERROR",
				truncateDetail(detail != null ? detail : "Voir les logs serveur.")));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> fallback(Exception ex) {
		log.error("Unhandled exception", ex);
		ResponseEntity<ErrorResponse> mapped = mapKnownCauses(ex);
		if (mapped != null) {
			return mapped;
		}
		ErrorResponse body = ErrorResponse.of(
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
			"INTERNAL_ERROR",
			"Une erreur interne s'est produite.");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
	}

	private ResponseEntity<ErrorResponse> mapKnownCauses(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof DataAccessException dae) {
				return dataAccess(dae);
			}
			if (t instanceof PersistenceException pe) {
				return persistence(pe);
			}
			if (t instanceof JDBCException jdbc) {
				SQLException sql = jdbc.getSQLException();
				String detail = sql != null && sql.getMessage() != null ? sql.getMessage() : jdbc.getMessage();
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(databaseErrorBody(detail));
			}
			if (t instanceof SQLException sql) {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(databaseErrorBody(sql.getMessage()));
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return null;
	}

	private static ErrorResponse databaseErrorBody(String detailRaw) {
		String detail = truncateDetail(detailRaw);
		if (detail != null && detail.contains("ETATS")) {
			detail += " Exécutez le script oracle-etats-boite.sql (table ETATS / SEQ_ID_ETAT).";
		}
		if (detail != null && detail.contains("ETAT_COURANT")) {
			detail += " Exécutez le script oracle-etats-migration-v2.sql (colonne ETAT_COURANT_ID sur BOITES).";
		}
		if (detail != null && (detail.contains("NOMBRE_BOITES") || detail.contains("nombre_boites"))) {
			detail += " Exécutez le script oracle-bordereaux-nombre-boites.sql "
				+ "(colonne NOMBRE_BOITES sur BORDEREAUX), puis redémarrez le backend.";
		}
		return ErrorResponse.of(
			HttpStatus.INTERNAL_SERVER_ERROR.value(),
			HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
			"DATABASE_ERROR",
			detail);
	}

	private static String truncateDetail(String detail) {
		if (detail == null || detail.isEmpty()) {
			return "Voir les logs serveur.";
		}
		return detail.length() > 400 ? detail.substring(0, 400) + "…" : detail;
	}
}

