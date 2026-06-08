package ommp.archives.audit;



/**

 * Codes d'action stockés dans {@code AUDIT_LOG.ACTION_CODE}.

 * <p>Liste exhaustive des mutations journalisées — toute nouvelle route POST/PUT/PATCH/DELETE

 * doit ajouter un code ici et dans {@link AuditMutationResolver}.

 */

public final class AuditAction {



	// Auth (connexion gérée dans AuthService, pas le filtre)

	public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";

	public static final String LOGIN_FAILED = "LOGIN_FAILED";

	public static final String USER_UPDATE = "USER_UPDATE";

	public static final String PROFILE_UPDATE = "PROFILE_UPDATE";

	public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";

	public static final String PASSWORD_RESET_APPROVE = "PASSWORD_RESET_APPROVE";

	public static final String PASSWORD_RESET_COMPLETE = "PASSWORD_RESET_COMPLETE";



	// Bordereaux

	public static final String BORDEREAU_CREATE = "BORDEREAU_CREATE";

	public static final String BORDEREAU_UPDATE = "BORDEREAU_UPDATE";

	public static final String BORDEREAU_VALIDATE = "BORDEREAU_VALIDATE";



	// Règles de conservation

	public static final String RULE_CREATE = "RULE_CREATE";

	public static final String RULE_UPDATE = "RULE_UPDATE";

	public static final String RULE_INVALIDATE = "RULE_INVALIDATE";

	public static final String RULE_REPLACE = "RULE_REPLACE";

	public static final String RULE_STATUS = "RULE_STATUS";



	// Types de document

	public static final String DOC_TYPE_CREATE = "DOC_TYPE_CREATE";

	public static final String DOC_TYPE_UPDATE = "DOC_TYPE_UPDATE";



	// Emplacements / épi

	public static final String EMPLACEMENT_CREATE = "EMPLACEMENT_CREATE";

	public static final String EMPLACEMENT_UPDATE = "EMPLACEMENT_UPDATE";

	public static final String EMPLACEMENT_DELETE = "EMPLACEMENT_DELETE";

	public static final String EMPLACEMENT_IMPORT = "EMPLACEMENT_IMPORT";



	// Agents

	public static final String AGENT_ACTIVE = "AGENT_ACTIVE";



	// Boîtes

	public static final String BOITE_ALERT_SNOOZE = "BOITE_ALERT_SNOOZE";

	public static final String BOITE_APPROVE_DESTRUCTION = "BOITE_APPROVE_DESTRUCTION";

	public static final String DOSSIER_CREATE = "DOSSIER_CREATE";

	public static final String DOSSIER_DELETE = "DOSSIER_DELETE";



	// Notifications agent (bordereau validé)

	public static final String NOTIFICATION_DISMISS = "NOTIFICATION_DISMISS";

	public static final String NOTIFICATION_DISMISS_ALL = "NOTIFICATION_DISMISS_ALL";



	private AuditAction() {

	}

}


