package ommp.archives.audit;



import java.util.regex.Matcher;

import java.util.regex.Pattern;



import org.springframework.stereotype.Component;



/**

 * Traduit une requête HTTP mutante en entrée d'audit métier.

 * Retourne {@code null} si la route n'est pas une mutation métier journalisée (ex. DELETE non exposé en UI).

 */

@Component

public class AuditMutationResolver {



	private static final Pattern NUMERIC_ID = Pattern.compile("/(\\d+)(?:/|$)");



	public AuditEntry resolve(String httpMethod, String requestUri) {

		String method = httpMethod == null ? "" : httpMethod.toUpperCase();

		String path = normalizePath(requestUri);



		if (path.startsWith("/api/bordereaux")) {

			return resolveBordereau(method, path);

		}

		if (path.startsWith("/api/conservation-rules")) {

			return resolveConservationRule(method, path);

		}

		if (path.startsWith("/api/auth")) {

			return resolveAuth(method, path);

		}

		if (path.startsWith("/api/document-types")) {

			return resolveDocumentType(method, path);

		}

		if (path.startsWith("/api/emplacements")) {

			return resolveEmplacement(method, path);

		}

		if (path.startsWith("/api/agents")) {

			return resolveAgent(method, path);

		}

		if (path.startsWith("/api/boites")) {

			return resolveBoite(method, path);

		}

		if (path.startsWith("/api/notifications")) {

			return resolveNotification(method, path);

		}

		return null;

	}



	private AuditEntry resolveBordereau(String method, String path) {

		if (path.contains("/valider-affectation") && "POST".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.BORDEREAU_VALIDATE,

				AuditResourceType.BORDEREAU,

				"Validation et affectation du bordereau " + labelId(id)

			);

		}

		if ("POST".equals(method) && path.equals("/api/bordereaux")) {

			return new AuditEntry(

				AuditAction.BORDEREAU_CREATE,

				AuditResourceType.BORDEREAU,

				"Création d'un bordereau de transfert"

			);

		}

		if ("PUT".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.BORDEREAU_UPDATE,

				AuditResourceType.BORDEREAU,

				"Modification du bordereau " + labelId(id)

			);

		}

		return null;

	}



	private AuditEntry resolveConservationRule(String method, String path) {

		if (path.contains("/invalidate") && "POST".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.RULE_INVALIDATE,

				AuditResourceType.CONSERVATION_RULE,

				"Invalidation de la règle de conservation " + labelId(id)

			);

		}

		if (path.contains("/replace") && "POST".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.RULE_REPLACE,

				AuditResourceType.CONSERVATION_RULE,

				"Remplacement de la règle de conservation " + labelId(id)

			);

		}

		if (path.contains("/status") && "PATCH".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.RULE_STATUS,

				AuditResourceType.CONSERVATION_RULE,

				"Changement de statut de la règle " + labelId(id)

			);

		}

		if ("POST".equals(method) && path.equals("/api/conservation-rules")) {

			return new AuditEntry(

				AuditAction.RULE_CREATE,

				AuditResourceType.CONSERVATION_RULE,

				"Création d'une règle de conservation"

			);

		}

		if ("PUT".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.RULE_UPDATE,

				AuditResourceType.CONSERVATION_RULE,

				"Modification de la règle de conservation " + labelId(id)

			);

		}

		return null;

	}



	private AuditEntry resolveAuth(String method, String path) {

		if (path.startsWith("/api/auth/users/") && "PUT".equals(method)) {

			String id = segmentAfter(path, "/api/auth/users/");

			return new AuditEntry(

				AuditAction.USER_UPDATE,

				AuditResourceType.USER,

				"Modification du compte " + labelId(id)

			);

		}

		if (path.equals("/api/auth/profile") && "PUT".equals(method)) {

			return new AuditEntry(AuditAction.PROFILE_UPDATE, AuditResourceType.USER, "Mise à jour du profil connecté");

		}

		if (path.startsWith("/api/auth/profile/") && "PUT".equals(method)) {

			String userName = segmentAfter(path, "/api/auth/profile/");

			return new AuditEntry(

				AuditAction.PROFILE_UPDATE,

				AuditResourceType.USER,

				"Mise à jour du profil de l'utilisateur " + labelId(userName)

			);

		}

		return null;

	}



	private AuditEntry resolveDocumentType(String method, String path) {

		if ("POST".equals(method) && path.equals("/api/document-types")) {

			return new AuditEntry(

				AuditAction.DOC_TYPE_CREATE,

				AuditResourceType.DOCUMENT_TYPE,

				"Création d'un type de document"

			);

		}

		if ("PUT".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.DOC_TYPE_UPDATE,

				AuditResourceType.DOCUMENT_TYPE,

				"Modification du type de document " + labelId(id)

			);

		}

		return null;

	}



	private AuditEntry resolveEmplacement(String method, String path) {

		if (path.contains("/import") && "POST".equals(method)) {

			return new AuditEntry(

				AuditAction.EMPLACEMENT_IMPORT,

				AuditResourceType.EMPLACEMENT,

				"Import Excel d'épi(s)"

			);

		}

		if (path.equals("/api/emplacements/epis") && "POST".equals(method)) {

			return new AuditEntry(

				AuditAction.EMPLACEMENT_CREATE,

				AuditResourceType.EMPLACEMENT,

				"Création d'un épi"

			);

		}

		if (path.contains("/resize") && "PUT".equals(method)) {

			String id = firstNumericId(path);

			return new AuditEntry(

				AuditAction.EMPLACEMENT_UPDATE,

				AuditResourceType.EMPLACEMENT,

				"Redimensionnement de l'épi " + labelId(id)

			);

		}

		if ((path.contains("/rows") || path.contains("/columns")) && ("POST".equals(method) || "DELETE".equals(method))) {

			String id = firstNumericId(path);

			String action = "DELETE".equals(method) ? AuditAction.EMPLACEMENT_DELETE : AuditAction.EMPLACEMENT_UPDATE;

			return new AuditEntry(

				action,

				AuditResourceType.EMPLACEMENT,

				("DELETE".equals(method) ? "Suppression" : "Ajout")

					+ " structure épi "

					+ labelId(id)

			);

		}

		if (path.contains("/epis/last") && "DELETE".equals(method)) {

			return new AuditEntry(

				AuditAction.EMPLACEMENT_DELETE,

				AuditResourceType.EMPLACEMENT,

				"Suppression du dernier épi"

			);

		}

		return null;

	}



	private AuditEntry resolveAgent(String method, String path) {

		if (path.contains("/active") && "PATCH".equals(method)) {

			String id = segmentAfter(path, "/api/agents/").replace("/active", "");

			return new AuditEntry(

				AuditAction.AGENT_ACTIVE,

				AuditResourceType.AGENT,

				"Activation / désactivation de l'agent " + labelId(id)

			);

		}

		return null;

	}



	private AuditEntry resolveBoite(String method, String path) {

		if (path.contains("/dossiers") && "POST".equals(method)) {

			String boiteId = firstNumericId(path);

			return new AuditEntry(

				AuditAction.DOSSIER_CREATE,

				AuditResourceType.DOSSIER,

				"Ajout d'un dossier dans la boîte " + labelId(boiteId)

			);

		}

		if (path.contains("/dossiers/") && "DELETE".equals(method)) {

			String boiteId = firstNumericId(path);

			String dossierId = lastNumericId(path);

			return new AuditEntry(

				AuditAction.DOSSIER_DELETE,

				AuditResourceType.DOSSIER,

				"Suppression du dossier " + labelId(dossierId) + " (boîte " + labelId(boiteId) + ")"

			);

		}

		String id = firstNumericId(path);

		if (path.contains("reporter-alerte-semi-actif") && "POST".equals(method)) {

			return new AuditEntry(

				AuditAction.BOITE_ALERT_SNOOZE,

				AuditResourceType.BOITE,

				"Report d'alerte semi-active — boîte " + labelId(id)

			);

		}

		if (path.contains("approuver-destruction-transfert") && "POST".equals(method)) {

			return new AuditEntry(

				AuditAction.BOITE_APPROVE_DESTRUCTION,

				AuditResourceType.BOITE,

				"Approbation destruction / transfert — boîte " + labelId(id)

			);

		}

		return null;

	}



	private AuditEntry resolveNotification(String method, String path) {

		if (!"DELETE".equals(method)) {

			return null;

		}

		if (path.equals("/api/notifications")) {

			return new AuditEntry(

				AuditAction.NOTIFICATION_DISMISS_ALL,

				AuditResourceType.NOTIFICATION,

				"Marquage de toutes les notifications comme lues"

			);

		}

		if (path.startsWith("/api/notifications/")) {

			String id = segmentAfter(path, "/api/notifications/");

			return new AuditEntry(

				AuditAction.NOTIFICATION_DISMISS,

				AuditResourceType.NOTIFICATION,

				"Marquage de la notification " + labelId(id) + " comme lue"

			);

		}

		return null;

	}



	private static String normalizePath(String uri) {

		if (uri == null || uri.isBlank()) {

			return "";

		}

		int q = uri.indexOf('?');

		String p = q >= 0 ? uri.substring(0, q) : uri;

		if (p.length() > 1 && p.endsWith("/")) {

			p = p.substring(0, p.length() - 1);

		}

		return p.toLowerCase();

	}



	private static String firstNumericId(String path) {

		Matcher m = NUMERIC_ID.matcher(path);

		return m.find() ? m.group(1) : null;

	}



	private static String lastNumericId(String path) {

		Matcher m = NUMERIC_ID.matcher(path);

		String last = null;

		while (m.find()) {

			last = m.group(1);

		}

		return last;

	}



	private static String segmentAfter(String path, String prefix) {

		if (!path.startsWith(prefix)) {

			return null;

		}

		String rest = path.substring(prefix.length());

		int slash = rest.indexOf('/');

		return slash >= 0 ? rest.substring(0, slash) : rest;

	}



	private static String labelId(String id) {

		return id == null || id.isBlank() ? "" : "#" + id;

	}

}


