package ommp.archives.entity;

/**
 * Valeurs de {@code REGLES_CONSERVATION.STATUT}.
 * <p>
 * Au plus une règle {@link #VALIDE} par {@link DocumentType} ; les {@link #INVALIDE} restent en historique.
 */
public enum ConservationRuleStatus {

	/** Règle active pour le type de document. */
	VALIDE,

	/** Règle remplacée / désactivée. */
	INVALIDE
}
