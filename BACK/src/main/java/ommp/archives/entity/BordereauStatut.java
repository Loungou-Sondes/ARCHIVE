package ommp.archives.entity;

/**
 * Valeurs de la colonne {@code BORDEREAUX.STATUT} — flux de saisie et d'occupation des blocs.
 * <p>
 * Distinct de {@link BoiteEtatType} ({@code ETATS.TYPE_ETAT}, cycle documentaire de chaque boîte).
 */
public enum BordereauStatut {

	/** Bordereau validé avec emplacements (ou créé directement affecté). */
	AFFECTE,

	/** Bordereau sauvegardé sans réserver de blocs ; reprise via « Assigner ». */
	EN_ATTENTE
}
