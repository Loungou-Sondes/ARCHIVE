package ommp.archives.entity;

/**
 * Valeurs de la colonne {@code ETATS.TYPE_ETAT} (cycle documentaire de la boîte).
 * <p>
 * Ne pas confondre avec :
 * <ul>
 *   <li>{@link FinalDecision} — colonne {@code REGLES_CONSERVATION.FINAL_DECISION} (politique)</li>
 *   <li>{@link BordereauStatut} — colonne {@code BORDEREAUX.STATUT} (emplacements / bordereau)</li>
 * </ul>
 * Alertes échéance destruction/transfert : filtre {@link #SEMI_ACTIF} via {@link Boite#getEtatCourant()}.
 */
public enum BoiteEtatType {

	/** État initial à la création ; conservé après assignation aux blocs. */
	SEMI_ACTIF,

	/** Approbation admin — libère {@code EMPLACEMENTS.ID_BOITE}. */
	TRANSFERT,

	/** Approbation admin — libère {@code EMPLACEMENTS.ID_BOITE}. */
	DESTRUCTION;

	/** Libellé UI (participe passé pour les états de fin de cycle). */
	public String displayLabel() {
		return switch (this) {
			case SEMI_ACTIF -> "Semi-actif";
			case TRANSFERT -> "Transféré";
			case DESTRUCTION -> "Détruit";
		};
	}
}
