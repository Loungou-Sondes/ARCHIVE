package ommp.archives.entity;

/**
 * Valeurs {@code USER_DETAILS.STATUS_ID} pour l’activation du compte agent.
 * <ul>
 *   <li>{@link #ACTIF} — connexion autorisée</li>
 *   <li>{@link #INACTIF} — connexion refusée</li>
 * </ul>
 * {@code null} est traité comme actif (comptes existants avant la convention).
 */
public final class AgentStatusCodes {

	public static final long ACTIF = 1L;
	public static final long INACTIF = 0L;

	private AgentStatusCodes() {
	}

	public static boolean isActive(Long statusId) {
		return statusId == null || statusId == ACTIF;
	}
}
