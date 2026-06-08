package ommp.archives.entity;

/**
 * Valeurs de {@code REGLES_CONSERVATION.DECISION_FINALE} — politique de fin de vie du document.
 * <p>
 * Distinct de {@link BoiteEtatType} ({@code ETATS.TYPE_ETAT} = ce qui s'est passé sur une boîte).
 */
public enum FinalDecision {

	CONSERVER,
	DETRUIRE,
	TRANSFERER
}
