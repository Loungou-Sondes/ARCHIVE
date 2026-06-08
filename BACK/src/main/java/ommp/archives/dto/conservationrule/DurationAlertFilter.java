package ommp.archives.dto.conservationrule;

/**
 * Filtre optionnel liste règles (legacy). Le hub Alertes n’affiche plus les règles à durée active inconnue.
 */
public enum DurationAlertFilter {

	/** Ne retourne aucune règle (alertes règles retirées du hub). */
	ONLY,
	/** Aucun filtre supplémentaire. */
	EXCLUDE
}
